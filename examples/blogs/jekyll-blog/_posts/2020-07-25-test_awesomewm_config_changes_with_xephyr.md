---
title: "Test awesomeWM config changes with Xephyr"
date: 2020-07-25
---


I started with this wrapper package: https://github.com/serialoverflow/awmtt

https://www.reddit.com/r/awesomewm/comments/et9gnl/using_xephyr_as_external_monitor/
In a few places (reddit, awesome docs) I've seen recommendations to use Xephyr
to test your config before doing a proper restart. Not sure how i feel about
that.

--
2020-07-25 16:10

debugging awesome is a bit of a shit show. I have no idea how the maintainers do
it, but I haven't seen much help in the docs.

Thank god for the above tools.

I'm finally seeing `print` output by redirecting awesome stdout/stderr into a
file i'm tailing.

``` sh
awmtt start -a >> `/.config/awesome/stdout 2>> `/.config/awesome/stdout
```

awesome's debug loop is not great - if it fails to startup, it doesn't keep the
old state - that state is gone, and it reverts to a default/fallback that is not
yours. Perhaps that could be customized? But i'd be tempted to put my whole
config in there instead...

The advice in the above reddit thread is create a seperate git repo to hack in
with a completely different machine user, and don't update your actual config
until you've 'pushed'. Seems a bit much.

The sooner i can get an nrepl experience going, the better... but i'm starting
to fear that it isn't possible/available/ever even attempted.