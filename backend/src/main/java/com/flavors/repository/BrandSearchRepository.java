package com.flavors.repository;

import com.flavors.util.SearchText;
import com.flavors.dto.BrandSearchCriteria;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Brand search as native SQL, built like {@link CreatorSearchRepository}: values are always bound. */
@Repository
public class BrandSearchRepository {

    private static final double FUZZY_THRESHOLD = 0.3;

    private final NamedParameterJdbcTemplate jdbc;

    public BrandSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPageRows<BrandSearchRow> search(BrandSearchCriteria c) {
        List<String> words = SearchText.words(c.q());
        MapSqlParameterSource params = new MapSqlParameterSource("fuzzy", FUZZY_THRESHOLD);
        StringBuilder where = new StringBuilder("u.status = 'ACTIVE'");
        List<String> scores = new ArrayList<>();

        for (int i = 0; i < words.size(); i++) {
            String w = ":w" + i;
            String like = ":l" + i;
            params.addValue("w" + i, words.get(i));
            params.addValue("l" + i, SearchText.containsPattern(words.get(i)));
            // A word may match the brand itself or any one of its branches.
            where.append(" AND (")
                    .append(matches("b.business_name", w, like)).append(" OR ")
                    .append(matches("COALESCE(b.cuisine, '')", w, like)).append(" OR ")
                    .append(matches("COALESCE(b.description, '')", w, like)).append(" OR EXISTS (SELECT 1 FROM branch br WHERE br.brand_id = b.user_id AND (")
                    .append(matches("br.name", w, like)).append(" OR ")
                    .append(matches("br.location", w, like)).append(")))");
            scores.add("GREATEST(" + score("b.business_name", w, like, "1.0") + ", "
                    + score("COALESCE(b.cuisine, '')", w, like, "1.5") + ", "
                    + score("COALESCE(b.description, '')", w, like, "0.5") + ", "
                    + "COALESCE((SELECT max(GREATEST(" + score("br.name", w, like, "0.7") + ", "
                    + score("br.location", w, like, "1.5") + ")) FROM branch br WHERE br.brand_id = b.user_id), 0))");
        }

        String cuisine = SearchText.cleanFilter(c.cuisine());
        if (cuisine != null) {
            where.append(" AND (b.cuisine ILIKE :cuisineLike ESCAPE '\\' OR similarity(lower(b.cuisine), lower(:cuisine)) >= 0.5)");
            params.addValue("cuisine", cuisine);
            params.addValue("cuisineLike", SearchText.containsPattern(cuisine));
        }
        String location = SearchText.cleanFilter(c.location());
        if (location != null) {
            where.append(" AND EXISTS (SELECT 1 FROM branch bl WHERE bl.brand_id = b.user_id AND (bl.location ILIKE :locationLike ESCAPE '\\' OR similarity(lower(bl.location), lower(:location)) >= 0.5))");
            params.addValue("location", location);
            params.addValue("locationLike", SearchText.containsPattern(location));
        }

        // Suspended brands are hidden, like unapproved creators in the other search.
        String from = " FROM brand b JOIN app_user u ON u.id = b.user_id WHERE " + where;
        Long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);

        String order = scores.isEmpty()
                ? "b.business_name, b.user_id"
                : "(" + String.join(" + ", scores) + ") DESC, b.business_name, b.user_id";
        params.addValue("limit", c.size());
        params.addValue("offset", (long) c.page() * c.size());
        List<BrandSearchRow> found = jdbc.query(
                "SELECT b.user_id, b.business_name, b.cuisine, b.description" + from
                        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params,
                (rs, n) -> new BrandSearchRow(rs.getLong("user_id"), rs.getString("business_name"),
                        rs.getString("cuisine"), rs.getString("description"), List.of()));

        // Branch locations in a second query (one per page), so the main query stays one row per brand.
        Map<Long, List<String>> locations = new LinkedHashMap<>();
        if (!found.isEmpty()) {
            jdbc.query("SELECT brand_id, location FROM branch WHERE brand_id IN (:ids) ORDER BY id",
                    new MapSqlParameterSource("ids", found.stream().map(BrandSearchRow::brandId).toList()),
                    rs -> {
                        locations.computeIfAbsent(rs.getLong("brand_id"), k -> new ArrayList<>())
                                .add(rs.getString("location"));
                    });
        }
        List<BrandSearchRow> rows = found.stream()
                .map(r -> new BrandSearchRow(r.brandId(), r.businessName(), r.cuisine(), r.description(),
                        locations.getOrDefault(r.brandId(), List.of())))
                .toList();
        return new SearchPageRows<>(rows, total == null ? 0 : total);
    }

    private static String matches(String column, String word, String like) {
        return column + " ILIKE " + like + " ESCAPE '\\' OR word_similarity(" + word + ", " + column + ") >= :fuzzy";
    }

    private static String score(String column, String word, String like, String weight) {
        return "(CASE WHEN " + column + " ILIKE " + like + " ESCAPE '\\' THEN 1.0 ELSE word_similarity("
                + word + ", " + column + ") END) * " + weight;
    }
}
