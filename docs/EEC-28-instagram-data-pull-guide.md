# Instagram creator data: how to pull it

Companion to `EEC-28-instagram-api-research.md`. The steps below follow Meta's Instagram API with
Instagram Login flow. They are **not verified against the live docs or tested**: Meta's dashboard changes
often, so check them against the "Getting Started" link in §8 of the research doc.

## What you need

1. **A Meta developer account.** Register at developers.facebook.com with a personal Facebook login. Free.
2. **A Meta app** of type Business, with the "Instagram API with Instagram Login" product added. One
   teammate owns it and adds the others as app roles.
3. **An Instagram Business or Creator account** to pull data from. A personal account won't work.
   Switching is free: Instagram → Settings → Account type and tools → Switch to professional account.
   No Facebook Page is required (research doc §2).

## Steps to pull data

1. **Add the Instagram test accounts to the app.** In the app dashboard, go to the Instagram product →
   API setup, then add each team-owned account as an Instagram tester or role. The account owner accepts
   the invite inside Instagram. While the app is in Standard Access, this lets the app act on those
   accounts without App Review (research doc §4).
2. **Get an access token.** In the same dashboard, use "Generate token" next to the account. The account
   owner logs in and approves `instagram_business_basic` and `instagram_business_manage_insights`. The
   token is short-lived: exchange it for a long-lived one (about 60 days, refreshable).
3. **Call the API** with that token:
   - Followers: `GET https://graph.instagram.com/me?fields=username,followers_count,media_count&access_token=...`
   - Recent media: `GET https://graph.instagram.com/me/media?fields=id,media_type,permalink,timestamp`
   - Per-post engagement: `GET https://graph.instagram.com/{media-id}/insights?metric=views,likes,comments,shares`

   Try these in curl or Postman first, before writing any Java.
4. **Build it into the backend.**
   - Store tokens encrypted, never in plain text or in the repo.
   - Add a scheduled job to refresh tokens and re-fetch stats.
   - Cache results in the DB rather than calling the API on every page view.

## Things to watch

- No live testing has been done yet. Step 3 will show which metrics actually return for each media type;
  some are unavailable for certain Reels and Stories.
- Insights can lag up to 48 hours.
- This only works with team-owned accounts for now. Letting independent creators connect their own
  accounts needs Advanced Access and Meta App Review, which is a later phase (research doc §4).
