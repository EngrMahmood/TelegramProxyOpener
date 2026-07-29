# Play Console Submission Checklist

You have a developer account already, so this is the remaining path from "app not listed" to "live." Do these roughly in order.

## 1. Create the app entry
Play Console → **Create app** → name "Telegram Proxy Opener" → Free → Confirm the declarations (Developer Program Policies, US export laws).

## 2. Store listing (Grow → Store presence → Main store listing)
- **App name**: Telegram Proxy Opener
- **Short description**: from [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md)
- **Full description**: from [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) — remember to fill in the privacy policy URL first
- **App icon**: `play_store_icon_512.png` (512×512)
- **Feature graphic**: `feature_graphic_1024x500.png` (1024×500)
- **Phone screenshots**: `playstore_ss1_main.png`, `playstore_ss2_scrolled.png` (min 2 required — these two are enough to start, add more later if you want)
- **Category**: Tools (or Communication) — Tools fits better since it's a utility, not a messaging app itself
- **Contact details**: your email (mahmood7692@gmail.com), no website/phone required
- **Privacy policy URL**: your published Google Sites page

## 3. App content (Policy → App content) — do each sub-section
- **Privacy policy**: paste the same URL again here
- **Ads**: declare **Yes, my app contains ads** (this must match reality — you have AdMob banner + interstitial)
- **Content rating**: fill out the IARC questionnaire honestly.
  - Category: **Utility, Productivity, Communication, or Other**
  - There's no violence/gambling/etc. content, so most answers are "No"
  - One thing to flag honestly if asked: the app facilitates connecting to third-party network proxies — answer questionnaire items about "unrestricted internet access" truthfully if it comes up
- **Target audience and content**: select an appropriate age range (13+ is reasonable given the proxy/circumvention use case is not really aimed at young children — avoid selecting "designed for children," which triggers stricter Families Policy requirements you don't want here)
- **Data safety**: this is the one most likely to trip people up — see section 4 below
- **Government app**: No
- **Financial features**: No
- **Health**: No (unless asked, skip)
- **News app**: No

## 4. Data Safety form — what to actually declare
Your app itself collects nothing, but **the AdMob SDK does** collect data for ads on your behalf. Answer based on what AdMob actually does:

- **Does your app collect or share any of the required user data types?** → **Yes**
- Under **Device or other IDs**: collected = Yes, purpose = **Advertising or marketing**, shared with third parties = **Yes** (shared with Google for ad serving)
- Under **App activity → App interactions** (ad impressions/clicks): optional but recommended to declare, purpose = Advertising
- **Is all user data encrypted in transit?** → Yes (HTTPS/TLS, standard for AdMob and your own network calls)
- **Do you provide a way for users to request data deletion?** → Since you don't collect personally identifiable data yourself, you can note this isn't applicable to app-specific data; AdMob's own opt-outs are handled by Google's ad settings, not your app

If this feels fiddly, Google has a built-in guide inside the Data Safety form that auto-suggests answers when you tell it you're using AdMob — use that, it keeps you aligned with what Google expects.

## 5. Release
- Go to **Release → Testing → Internal testing** first (recommended before production — lets you install via a private link and confirm everything works from a real Play-distributed build before the world sees it)
- Create a new release, upload `app/build/outputs/bundle/release/app-release.aab`
- Play will ask about **Play App Signing** — accept the default (Google re-signs your app with its own key for distribution, using your upload key only to verify updates come from you — this is standard and recommended, don't opt out)
- Add release notes (e.g. "Initial release")
- Save → Review release → Roll out to internal testing
- Add yourself (or testers) by email under the internal testing tab, install via the opt-in link, confirm it works
- When satisfied, promote the same release to **Production** (Release → Production → Create release → choose the tested build)

## 6. Pricing & distribution
- Free
- Select countries (all, or restrict if you're worried about specific jurisdictions' stance on proxy/circumvention tools — most countries are fine, but use your judgment)
- Contains ads: Yes (confirmed again here)

## 7. Submit for review
Once every section in **App content** and **Store listing** shows a green checkmark (no red/orange warnings), hit **Send for review** on the production release. Review typically takes anywhere from a few hours to a few days for a first submission.

## Known friction points for this specific app
- **Proxy/circumvention framing**: be straightforward in the questionnaire and description (already done above) rather than vague — Play reviewers are generally fine with proxy-utility apps as long as the listing doesn't claim anything misleading (e.g. don't claim it "unblocks everything" or targets a specific censorship regime by name)
- **AdMob approval status "Requires review"**: this resolves once your app is live and serving to real users for a bit — it's independent of Play Store review, don't block on it
