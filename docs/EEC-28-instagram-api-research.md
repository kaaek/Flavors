# EEC-28 — Instagram API Feasibility Research

**Project:** Flavors  
**Task:** SPIKE-1 — Social Platform API Access  
**Decision:** GO

## 1. Objective

Investigate whether Instagram provides an official API that allows Flavors to retrieve follower counts and engagement statistics from creators' accounts. This information will support verified creator portfolios and campaign performance tracking.

## 2. API Availability

Meta provides the Instagram API with Instagram Login, which supports Instagram Business and Creator accounts. The API allows applications to retrieve account information, media objects and engagement insights.

The API does not require a Facebook Page to be linked to the Instagram professional account.

## 3. Available Data

The following data is supported by the API:

| Data | API Field / Metric |
|---|---|
| Follower count | `followers_count` |
| Views | `views` |
| Likes | `likes` |
| Comments | `comments` |
| Shares | `shares` |

Follower counts can be retrieved through the Instagram User API. Engagement metrics are available through the Instagram Media Insights API.

The availability of individual engagement metrics depends on the media type, such as posts, Reels and Stories.

## 4. Permissions and App Review

The relevant permissions are:

- `instagram_business_basic`
- `instagram_business_manage_insights`

For the initial implementation, Flavors will use team-owned Instagram professional accounts. Standard Access should be sufficient, provided these accounts are properly authorized and configured in the Meta developer application.

Advanced Access and Meta App Review would be required for a future production version allowing independent creators to connect their accounts.

## 5. Rate Limits

Meta applies API usage limits, including Business Use Case rate limits, to Instagram API requests.

The initial implementation will use a small number of Instagram accounts and retrieve follower and engagement data periodically. Therefore, the expected request volume is low.

Actual API usage limits have not been tested.

## 6. Limitations

- Only Instagram Business and Creator accounts are supported.
- Engagement data may be delayed by up to 48 hours.
- Story insights have a limited availability period.
- Some metrics are available only for specific media types.
- Valid access tokens and appropriate permissions are required.
- Some engagement metrics exclude interactions from promoted or boosted content.

## 7. Conclusion

**Decision: GO**

Based on Meta's official documentation, Instagram provides the necessary API capabilities for Flavors.

The API supports retrieving follower counts and engagement statistics from authorized professional accounts. Standard Access is expected to be sufficient for the initial implementation using team-owned accounts.

This conclusion is based on documentation research. No live API testing or implementation was performed.

## 8. References

1. [Instagram Platform Overview](https://developers.facebook.com/docs/instagram-platform/)
2. [Instagram API with Instagram Login — Getting Started](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started)
3. [Instagram User API Reference](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user)
4. [Instagram Media Insights](https://developers.facebook.com/documentation/instagram-platform/api-reference/instagram-media/insights)
5. [Meta Graph API Rate Limiting](https://developers.facebook.com/docs/graph-api/overview/rate-limiting/)
