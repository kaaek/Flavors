package com.flavors.repository;

import com.flavors.dto.CreatorSearchCriteria;
import com.flavors.dto.CreatorSearchFacets;
import com.flavors.util.SearchText;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Creator search as native SQL: relevance ranking and typo tolerance (pg_trgm) don't fit JPQL.
 * The SQL text is built only from fixed fragments; every value the user typed is a bound
 * parameter, so there is no injection path.
 */
@Repository
public class CreatorSearchRepository {

    /** word_similarity() at or above this counts as a typo-tolerant match ("burgr", "beyrout"). */
    private static final double FUZZY_THRESHOLD = 0.3;

    private final NamedParameterJdbcTemplate jdbc;

    public CreatorSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPageRows<CreatorSearchRow> search(long brandId, CreatorSearchCriteria c) {
        List<String> words = SearchText.words(c.q());
        MapSqlParameterSource params = new MapSqlParameterSource("fuzzy", FUZZY_THRESHOLD);
        StringBuilder where = new StringBuilder("""
                cp.verification_status = 'APPROVED' AND u.status = 'ACTIVE'
                """);
        List<String> scores = new ArrayList<>();

        // Every word must match at least one searchable field; the score is the sum of each word's
        // best field, with category and location weighted above name and bio.
        for (int i = 0; i < words.size(); i++) {
            String w = ":w" + i;
            String like = ":l" + i;
            params.addValue("w" + i, words.get(i));
            params.addValue("l" + i, SearchText.containsPattern(words.get(i)));
            where.append(" AND (")
                    .append(matches("cp.display_name", w, like)).append(" OR ")
                    .append(matches("cp.food_category", w, like)).append(" OR ")
                    .append(matches("cp.location", w, like)).append(" OR ")
                    .append(matches("COALESCE(cp.bio, '')", w, like)).append(")");
            scores.add("GREATEST(" + score("cp.display_name", w, like, "1.0") + ", "
                    + score("cp.food_category", w, like, "1.5") + ", "
                    + score("cp.location", w, like, "1.5") + ", "
                    + score("COALESCE(cp.bio, '')", w, like, "0.5") + ")");
        }

        addTolerantFilter(where, params, "location", "cp.location", c.location());
        addTolerantFilter(where, params, "category", "cp.food_category", c.category());
        String platform = SearchText.cleanFilter(c.platform());
        if (platform != null) {
            where.append(" AND lower(sa.platform) = lower(:platform)");
            params.addValue("platform", platform);
        }
        // sa is null for a creator without a connected account, and a comparison with null is never
        // true, so any social filter leaves such creators out (they appear when no filter is set).
        if (c.minFollowers() != null) {
            where.append(" AND sa.followers >= :minFollowers");
            params.addValue("minFollowers", c.minFollowers());
        }
        if (c.maxFollowers() != null) {
            where.append(" AND sa.followers <= :maxFollowers");
            params.addValue("maxFollowers", c.maxFollowers());
        }
        if (c.minEngagement() != null) {
            where.append(" AND sa.engagement_rate >= :minEngagement");
            params.addValue("minEngagement", c.minEngagement());
        }
        // The creator's price range must overlap the brand's range (FR-F2.5).
        if (c.minPriceCents() != null) {
            where.append(" AND cp.price_max_cents >= :minPrice");
            params.addValue("minPrice", c.minPriceCents());
        }
        if (c.maxPriceCents() != null) {
            where.append(" AND cp.price_min_cents <= :maxPrice");
            params.addValue("maxPrice", c.maxPriceCents());
        }
        // No campaign data exists yet, so ROI and redemptions are unknown for everyone; a filter on
        // them can't be satisfied and excludes every creator (until the campaign stories land).
        if (c.minRoi() != null || c.minRedemptions() != null) {
            where.append(" AND FALSE");
        }

        String from = """
                FROM creator_profile cp
                JOIN app_user u ON u.id = cp.user_id
                LEFT JOIN LATERAL (
                    SELECT s.platform, s.followers, s.engagement_rate
                    FROM social_account s
                    WHERE s.creator_id = cp.user_id AND s.status = 'CONNECTED'
                    ORDER BY s.followers DESC NULLS LAST, s.id
                    LIMIT 1
                ) sa ON TRUE
                LEFT JOIN saved_creator sv ON sv.creator_id = cp.user_id AND sv.brand_id = :brandId
                WHERE\s""" + where;
        params.addValue("brandId", brandId);

        Long total = jdbc.queryForObject("SELECT count(*) " + from, params, Long.class);

        String order = scores.isEmpty()
                ? "cp.display_name, cp.user_id"
                : "(" + String.join(" + ", scores) + ") DESC, cp.display_name, cp.user_id";
        params.addValue("limit", c.size());
        params.addValue("offset", (long) c.page() * c.size());
        List<CreatorSearchRow> rows = jdbc.query("""
                SELECT cp.user_id, cp.display_name, cp.food_category, cp.location, sa.platform,
                       sa.followers, sa.engagement_rate, cp.price_min_cents, cp.price_max_cents,
                       cp.storefront_slug, (sv.creator_id IS NOT NULL) AS saved
                """ + from + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params,
                (rs, n) -> new CreatorSearchRow(
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getString("food_category"),
                        rs.getString("location"),
                        rs.getString("platform"),
                        rs.getObject("followers", Long.class),
                        rs.getBigDecimal("engagement_rate"),
                        rs.getLong("price_min_cents"),
                        rs.getLong("price_max_cents"),
                        rs.getString("storefront_slug"),
                        rs.getBoolean("saved")));
        return new SearchPageRows<>(rows, total == null ? 0 : total);
    }

    /** Distinct locations and categories of approved creators, for the filter suggestions. */
    public CreatorSearchFacets facets() {
        String base = "SELECT DISTINCT %1$s FROM creator_profile cp JOIN app_user u ON u.id = cp.user_id "
                + "WHERE cp.verification_status = 'APPROVED' AND u.status = 'ACTIVE' ORDER BY %1$s";
        return new CreatorSearchFacets(
                jdbc.getJdbcTemplate().queryForList(base.formatted("cp.location"), String.class),
                jdbc.getJdbcTemplate().queryForList(base.formatted("cp.food_category"), String.class));
    }

    private static void addTolerantFilter(StringBuilder where, MapSqlParameterSource params,
                                          String name, String column, String value) {
        String clean = SearchText.cleanFilter(value);
        if (clean == null) {
            return;
        }
        where.append(" AND (").append(column).append(" ILIKE :").append(name).append("Like ESCAPE '\\' OR ")
                .append("similarity(lower(").append(column).append("), lower(:").append(name).append(")) >= 0.5)");
        params.addValue(name, clean);
        params.addValue(name + "Like", SearchText.containsPattern(clean));
    }

    // ILIKE uses the trigram GIN index; word_similarity catches typos in the remaining rows.
    private static String matches(String column, String word, String like) {
        return column + " ILIKE " + like + " ESCAPE '\\' OR word_similarity(" + word + ", " + column + ") >= :fuzzy";
    }

    private static String score(String column, String word, String like, String weight) {
        return "(CASE WHEN " + column + " ILIKE " + like + " ESCAPE '\\' THEN 1.0 ELSE word_similarity("
                + word + ", " + column + ") END) * " + weight;
    }
}
