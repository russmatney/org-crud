---
title: "These emacs bindings are crazy"
date: 2020-07-15
tags:
  - note
  - emacs
---


``` resigned-to-emacs
Given enough time, the keybindings make sense.
```
# Chords
When I heard about 'chords', it felt like:

``` emacs-community
Before you learn about emacs, we're going to learn about music.
```

Once you start doing it, you come to understand the modifier key and letter are
a bit like a duo, and things starts to get better as you invent stories of how
this function came to be.

Later you find [M-x + ivy/counsel or helm](/notes/20200715194425-required_emacs_packages) and realize things are going to be ok.
By then you know a few chords and will maybe survive to reach org-mode.

Still though, It's a trek. And I often get C-x C-c / C-x C-s / C-c C-x mixed up,
and I think one of them closes emacs. I stay away from those guys.
# The thing that I really can't stand

Is when the emacs docs themselves use the keybindings to reference functions
that have names, and just happen to be mapped to some function somewhere, by
default. Now we've got a plethora of packages taking over each other's finger
territory.

Please reference function names!
# [How to find-replace globally in emacs](/notes/20200614192040-how_to_find_replace_globally_in_emacs)
# How to Open Today's Note in org-roam

SPC m m d t == localleader + m (as in roam) + 'd'ate + 't'oday.

localleader == leader + 'm'ode as a prefix for some mode.

I presume the 'r' after localleader is some other obvious thing.

Typing SPC m and reading the which-key popup reveals that it is 'r' for
 'r'efile.

[Which-key popup is an emacs game-changer](/notes/20200715194425-required_emacs_packages).