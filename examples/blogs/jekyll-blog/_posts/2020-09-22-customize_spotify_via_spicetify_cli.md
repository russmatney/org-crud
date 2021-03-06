---
title: "Customize Spotify via spicetify-cli"
date: 2020-09-22
---


Below are rough notes covering customization of your spotify client via
spicetify-cli. The stream was recorded and is available here: https://www.twitch.tv/videos/749039165

Links:

- spicetify-cli repo: https://github.com/khanhas/spicetify-cli
- community themes repo (link to the wiki's themes preview) https://github.com/morpheusthewhite/spicetify-themes/wiki/Themes-preview
- link to this post: http://russmatney.com/notes/20200922160031-customize_spotify_via_spicetify_cli/
- subreddit: /r/unixporn: https://www.reddit.com/r/unixporn/

Spicetify is a tool that allows you to customize the face of your spotify
desktop client.

Spotify uses electron as a base, so the client is roughly a web-app.

Spicetify provies some tooling to support a dev experience on top of the client
itself:

- Enables dev-mode, allowing element inspection and reload/refreshing
- Inject custom css, html, and javascript (themes, extension, and apps)

Stream outline:

- Show discovery via /r/unixporn

- Install spicetify-cli on linux
  (via directions in repo)

- Enable and Apply a custom theme or two

- Dribbblish extra install step and demo

- enable and test extensions
  - full-screen
  - shuffle+

- enable and test reddit custom app
  - add a few subreddits: triphop, jazzhop, idm

Some nice wins:

- nice set of themes to choose from
- simple discovery of new music via reddit custom app
- keyboardShortcuts.js extension
  - add vimium-style keyboard shortcuts to spotify