# Org CRUD

A tool for reading and writing org content via clojure, as well as converting
org to markdown.

> Ninety percent of everything is crud.
> – [Theodore Sturgeon](https://www.quotes.net/quote/53367)

## Status

Very alpha. I've recently namespaced the keys and flattened the props a bit.
Still waiting for things to settle.

I've used it internally for a few months, but the public api still needs to be
proven.

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
the exporter. You can see how ralphie consumes it [in the ralphie.notes
namespace](https://github.com/russmatney/ralphie/blob/f6432e433e7e447aa1c0784e62ade2935c557cfc/src/ralphie/notes.clj#L21)

## Features

- Babashka compatible!
- List nested or flattened org items
- Update existing org items
  - Updates by :ID:
  - Add/remove tags, properties
  - Change an item's name
- Delete org items
- Convert org files to markdown files

## Org Item Model

This library parses org "items" from a given `.org` file.

An example item looks something like:

```clojure
{:org/name "My org headline" ;; the name without the bullets/todo block
 :org/headline "** [ ] My org headline" ;; a raw headline
 :org/source-file "" ;; the file this item was parsed from
 :org/id #uuid "" ;; a unique id for the headline (parsed from the item's property bucket)
 :org/tags #{"some" "tags"}
 :org/level 2 ;; 1-6 or :level/root
 :org/body-string ;; raw body string
 :org/body '() ;; a list of parsed lines, straight from organum TODO document this structure
 :org/status :status/not-started ;; parsed based on the
 ;; also supports :status/in-progress, :status/done, :status/cancelled

 :org.prop/some-prop "some prop value" ;; props are lower-and-kebab-cased
 :org.prop/some-other-prop "some other prop value"
 :org.prop/created-at "2020-07-21T09:25:50-04:00[America/New_York]" ;; to be parsed by consumer

 :org/items '() ;; nested org-items (if parsed with the 'nested' helpers)

 ;; misc helper attrs
 :org/word-count 3 ;; a basic count of words in the name and body
 :org/urls '() ;; parsed urls from the body - helpful for some use-cases
}
```

Items were originally implemented to support individual org headlines, but have
been adapted to work with single org files as well (to fit org-roam tooling
use-cases).

## Usage

TODO do some work on this section!

You can see the test files for example usage.

I'm attempting to hold a public api at `org-crud.api`, but that is a WIP.

### Parsing

```clojure
(ns your.ns
 (:require [org-crud.api :as org-crud]))

;; a nested item represents an entire file, with items as children
(let [item (org-crud/path->nested-item "/path/to/file.org")]
  (println item))

;; parses every '.org' file in a directory into a list of nested items
(let [items (org-crud/dir->nested-items "/path/to/org/dir")]
  (println (first items)

;; 'flattened' items have no children - just a list of every headline
;; (starting with the root itself)
(let [items (org-crud/path->flattened-items "/path/to/file.org")]
  (println (first items)))
```

### Updating

Updates are performed with a passed item and an update map that resembles the
org-item itself. It will use the passed item's id and source-file to find the
item to be updated, merge the updates in memory, then rewrite it.

```clojure
(ns your.ns
 (:require [org-crud.api :as org-crud]))

(-> (org-crud/path->flattened-items "/path/to/file.org")
    second ;; grabbing some item
    (org-crud/update!
      {:org/name "new item name" ;; changing the item name
       :org/tags "newtag" ;; adding a new tag
       :org.prop/some-prop "some-prop-value"
      }))
```

TODO document props-as-lists features
TODO document refile!, add-item!, delete-item!

## Notes

### Item IDs (UUIDs)

Item IDs are more or less required for updating. Things will fallback to
matching on name if there are no ids, but this approach has a few issues,
because names are not necessarily unique throughout files.

I've updated my personal org templates/snippets in places to include IDs when
creating new items, and org-mode provides helpers that can be used to add them
without too much trouble. (Ex: `org-id-get-create`).

TODO share links to templates/snippets that create uuids

If this is a problem, let me know, there are other workarounds. Using IDs allows
for cases with repeated headlines in the same file - otherwise you might get
into tracking line numbers or parents, which did not seem worth it, especially
as my usage benefitted from the IDs elsewhere.

## Relevant/Related tools

- [ox-hugo](https://github.com/kaushalmodi/ox-hugo)
- [organum](https://github.com/gmorpheme/organum)
- [org-roam](https://github.com/org-roam/org-roam)
