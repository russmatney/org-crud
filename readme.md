# Org CRUD

A tool for reading and writing org content via clojure, as well as converting
org to markdown.

> Ninety percent of everything is crud.
> – [Theodore Sturgeon](https://www.quotes.net/quote/53367)

## Status

Brand new. Used internally for a few months, freshly broken into its own
library. The public api still needs to be proven.

It works for my current use-cases, and there are unit tests! Feel free to take
it for a spin.

## Motivation

This library was pulled out of another tool, a productivity app built on top of
org-mode. That tool needed to be able to treat org files like a database of
items.

It was pulled out to make it easy to convert org files to markdown, and
targetted a use-case for publishing my
[org-roam](https://github.com/org-roam/org-roam/) directory. Emacs/Org supports
export like this as well, but because I already had most of this, it wasn't too
much more to write the `org-crud.markdown` namespace.

Org-crud aims to provide simple interactions with org files to clojure code.

## Background

There is not much to the parser besides a thin layer on top of
[organum](https://github.com/gmorpheme/organum). Organum does not nest the org
items - it returns a flattened list, regardless of the items' hierarchical
relationship. Org-crud provides both a flattened and nested option for parsing
org items.

This library is also [babashka](https://github.com/borkdude/babashka)
compatible, so you can drop it into a bb script without issue. This was
necessary for tools like [ralphie](https://github.com/russmatney/ralphie) to run
the exporter. You can see how ralphie consumes it [in the ralphie.garden
namespace](https://github.com/russmatney/ralphie/blob/e67ab9be12731ff0d6418a63357053b6e841f2a4/src/ralphie/garden.clj#L12).

## Features

- Babashka compatible!
- List nested or flattened org items
- Update existing org items
  - Updates by :ID:
  - Add/remove tags, properties
  - Change an item's name
- Delete org items
- Convert org files to markdown files

## Relevant tools

- [ox-hugo](https://github.com/kaushalmodi/ox-hugo)
- [organum](https://github.com/gmorpheme/organum)
