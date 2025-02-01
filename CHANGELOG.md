# CHANGELOG


## Untagged


## 


### 5 Jan 2025

- ([`b7ac581`](https://github.com/russmatney/org-crud/commit/b7ac581)) chore: update deps - Russell Matney

  > Couple failing tests...

- ([`b3d9594`](https://github.com/russmatney/org-crud/commit/b3d9594)) fix: no longer `.ext` comparison - Russell Matney

  > not sure when this changed?

- ([`ab0d4fb`](https://github.com/russmatney/org-crud/commit/ab0d4fb)) cruft: clj-kondo hooks - Russell Matney

### 14 Jul 2023

- ([`08279ca`](https://github.com/russmatney/org-crud/commit/08279ca)) feat: include plain context and match strings with links - Russell Matney

  > Links that break across lines are not quite right yet.

- ([`ebc18df`](https://github.com/russmatney/org-crud/commit/ebc18df)) feat: rough link context gathering - Russell Matney

  > These tests are kind of pain, could probably use a tighter sample input

- ([`6756461`](https://github.com/russmatney/org-crud/commit/6756461)) chore: update outdated deps - Russell Matney

### 1 May 2023

- ([`3640e10`](https://github.com/russmatney/org-crud/commit/3640e10)) fix: one more bad link - Russell Matney
- ([`d696c56`](https://github.com/russmatney/org-crud/commit/d696c56)) chore: fix bad syntax in readme links - Russell Matney

### 25 Apr 2023

- ([`21122a4`](https://github.com/russmatney/org-crud/commit/21122a4)) fix: crashing log, reset-last-modified in test - Russell Matney

  > Fixes some test fails that show the refile feature was actually broken.
  > 
  > Adds the :reset-last-modified feat to the write-update-roundtrip test,
  > which has been failing :file/last-modified was added to parsed child
  > nodes.


### 24 Apr 2023

- ([`4de20dd`](https://github.com/russmatney/org-crud/commit/4de20dd)) feat: :org/level-int - Russell Matney

  > Sometimes an integer is useful, like when re-rendering org notes in some
  > ui.

- ([`57f1190`](https://github.com/russmatney/org-crud/commit/57f1190)) refactor: more roam file move helper cleanup - Russell Matney
- ([`0cad544`](https://github.com/russmatney/org-crud/commit/0cad544)) feat: user script for moving roam files around - Russell Matney

  > Probably refile attempts to handle this for us, but w/e.


### 23 Apr 2023

- ([`41bcd78`](https://github.com/russmatney/org-crud/commit/41bcd78)) wip: towards a user/merge-org-roam-files bb script - Russell Matney

### 21 Apr 2023

- ([`855c200`](https://github.com/russmatney/org-crud/commit/855c200)) fix: include file/last-modified on child nodes - Russell Matney

  > To support easier lookup from datascript.


### 20 Apr 2023

- ([`acfc184`](https://github.com/russmatney/org-crud/commit/acfc184)) fix: imprecise datetime comparator in test - Russell Matney

### 19 Apr 2023

- ([`33356d2`](https://github.com/russmatney/org-crud/commit/33356d2)) fix: use name-string in :org/parent-name(s) - Russell Matney

  > Also includes :org/short-path on all nodes, not just root nodes.


### 5 Apr 2023

- ([`0073d95`](https://github.com/russmatney/org-crud/commit/0073d95)) feat: :org.update/reset-last-modified maintains modified at - Russell Matney

  > A new key can be included in the update map like:
  > 
  > `(update! path item (assoc up-map :org.update/reset-last-modified true)`
  > to update the file and reset the modified-at, maintaining the original
  > last-modified date.
  > 
  > Very useful for after-the-fact updates, like adding a 'published-at' to
  > all published posts without marking every published post as updated
  > today.


### 4 Apr 2023

- ([`d6724bc`](https://github.com/russmatney/org-crud/commit/d6724bc)) feat: stricter filter on what an image :text looks like - Russell Matney

  > Roam links with anything extension-like (e.g. a `hithere.com`) was
  > passing through looking like an image. This tightens the filter and adds
  > a unit test.
  > 
  > Also adds a unit test to be sure images are parsed properly when
  > preceded directly by text.

- ([`fcc107b`](https://github.com/russmatney/org-crud/commit/fcc107b)) feat: call fs/expand-home on paths before parsing - Russell Matney

  > Adds a bit more convenience to org-crud's api

- ([`2d015f0`](https://github.com/russmatney/org-crud/commit/2d015f0)) fix: don't crash on top-level images - Russell Matney

  > Top-level images were crashing b/c of a string/includes? on nil. Nil
  > punning would have resulted in the same (desired) behavior. Well, too bad.
  > 
  > Adds tests making sure top level images and multiple images in the same
  > node parse as expected.

- ([`975ae1c`](https://github.com/russmatney/org-crud/commit/975ae1c)) feat: parse date-string from image file paths - Russell Matney

  > Also adds image/file-expanded to save the trouble of fs/expand-home on
  > image paths later.


### 3 Apr 2023

- ([`94d7c9b`](https://github.com/russmatney/org-crud/commit/94d7c9b)) test: make sure image data is maintained across updates - Russell Matney

  > This seems fine. Updating image data via org-crud: not yet supported,
  > unless you update the :org/body directly yourself.

- ([`0a34074`](https://github.com/russmatney/org-crud/commit/0a34074)) feat: node parses :image/{path,extension,props} - Russell Matney

  > Now parsing image data and properties. For now these probably get
  > dropped in update - supporting that next.

- ([`68db174`](https://github.com/russmatney/org-crud/commit/68db174)) fix: org/name-string supporting multiple links in a line - Russell Matney

  > The regex was greedily grabbing from the first to the last link, cutting
  > the middle out of some name-strings.


### 24 Mar 2023

- ([`a3b1f78`](https://github.com/russmatney/org-crud/commit/a3b1f78)) chore: misc todos touched - Russell Matney

### 23 Mar 2023

- ([`b21af66`](https://github.com/russmatney/org-crud/commit/b21af66)) feat: update outdated deps - Russell Matney

### 17 Mar 2023

- ([`c614499`](https://github.com/russmatney/org-crud/commit/c614499)) wip: optional drop-links, drop-id-links - Russell Matney

  > Needs unit test (and actual test).


### 31 Jan 2023

- ([`654d62e`](https://github.com/russmatney/org-crud/commit/654d62e)) fix: prevent quote blocks from being dropped - Russell Matney

  > Updates were similarly dropping quote blocks - they are now supported
  > and have basic test coverage.

- ([`4c40b37`](https://github.com/russmatney/org-crud/commit/4c40b37)) chore: misc linter corrections - Russell Matney

  > This file is unused, so it doesn't matter much for now.

- ([`d385720`](https://github.com/russmatney/org-crud/commit/d385720)) fix: preserve src blocks with SRC or src block type - Russell Matney

  > The lower-case 'begin_src' blocks were being dropped - this adds test
  > coverage and makes sure both are supported.


### 5 Jan 2023

- ([`fd081de`](https://github.com/russmatney/org-crud/commit/fd081de)) fix: prevent priority from being read as :org/name - Russell Matney

  > This was happening when todos used the bracket syntax. Priority tests
  > should generally be run on bracketed todos anyway, as it's the most
  > common usage for priorities.

- ([`bb9dfac`](https://github.com/russmatney/org-crud/commit/bb9dfac)) fix: add/remove :org/priority, plus unit test - Russell Matney
- ([`0720a7f`](https://github.com/russmatney/org-crud/commit/0720a7f)) feat: support updating org priority - Russell Matney

  > I owe a unit test for this, but it's simple enough to impl.

- ([`4b28efe`](https://github.com/russmatney/org-crud/commit/4b28efe)) docs: improve update! docstring for tag crud - Russell Matney

### 30 Nov 2022

- ([`3e3c096`](https://github.com/russmatney/org-crud/commit/3e3c096)) feat: support :org/name-string key - Russell Matney

  > org links in headlines can be annoying - no reason to pass that on to
  > all consumers - this way we can just use :org/name-string when we don't
  > care for a fancy inline org link.


### 16 Oct 2022

- ([`c0351f0`](https://github.com/russmatney/org-crud/commit/c0351f0)) feat: add a few more always-exclude tags - Russell Matney

  > Org items and files with these tags will never be returned by org-crud
  > functions.


### 11 Oct 2022

- ([`3204a84`](https://github.com/russmatney/org-crud/commit/3204a84)) fix: include more line-types in body string - Russell Matney

  > links in bullets were being ignored, b/c the body-string used to parse
  > out links was only include :table-row line-types. This adds more line
  > types to that comparison. These new types are informed by organum.core's
  > parser types.


### 10 Oct 2022

- ([`be6a2d1`](https://github.com/russmatney/org-crud/commit/be6a2d1)) feat: expose flattener helper - Russell Matney

  > Could go for a better name here at some point.


### 6 Oct 2022

- ([`de4d19a`](https://github.com/russmatney/org-crud/commit/de4d19a)) feat: pulls some props set in clawe back to org-crud.node - Russell Matney
- ([`f9bb060`](https://github.com/russmatney/org-crud/commit/f9bb060)) feat: extend ->md support with :id->link-uri, etc - Russell Matney

  > Exposes better link-building support when converting org to markdown.
  > These functions can be used to look up linked docs by id and return a
  > uri path that makes sense, or return nil to have the link dropped
  > completely.


### 5 Oct 2022

- ([`cba7e95`](https://github.com/russmatney/org-crud/commit/cba7e95)) fix: support lowercase `src` :block-type - Russell Matney

  > Restores code blocks in org->markdown conversion.

- ([`43da3e7`](https://github.com/russmatney/org-crud/commit/43da3e7)) feat: always filter :private: tagged items and files - Russell Matney

  > Adds a `reject-items` helper for applying a `remove` to parsed org
  > items - this supports and item -> boolean `reject-p` function.
  > 
  > A default `reject-p` will always be included, ensuring org items tagged
  > :private: (at the header or filetags level) will always be filtered.
  > 
  > Additional tags to reject items with can be passed via an
  > `:exclude-tags` set.


### 28 Sep 2022

- ([`11069d5`](https://github.com/russmatney/org-crud/commit/11069d5)) chore: remove unused vars - Russell Matney
- ([`0aa2242`](https://github.com/russmatney/org-crud/commit/0aa2242)) test: add test covering headline links, scheduled metadata - Russell Matney

### 27 Sep 2022

- ([`b5ce429`](https://github.com/russmatney/org-crud/commit/b5ce429)) wip: pulls over organum impl for refactor - Russell Matney

  > Also adds a dropped item test case

- ([`bb210ef`](https://github.com/russmatney/org-crud/commit/bb210ef)) feat: rearrange organum.core pre-refactor - Russell Matney
- ([`5c3d48c`](https://github.com/russmatney/org-crud/commit/5c3d48c)) refactor: test refactored to use parse-lines - Russell Matney

  > rather than test the internal function, we refactor to take raw org
  > lines as input.

- ([`3fa7b79`](https://github.com/russmatney/org-crud/commit/3fa7b79)) feat: init malli schema for org items - Russell Matney

  > Includes a malli valid test helper for ensuring the new schema is being
  > returned (in tests).

- ([`01b1372`](https://github.com/russmatney/org-crud/commit/01b1372)) test: parse coverage for status, tags, misc props - Russell Matney

  > Writing tests for the parse-lines func explicitly now - that should be
  > the simplest place to work with complex org input, and should return
  > fully-baked org-crud shapes.

- ([`c28683d`](https://github.com/russmatney/org-crud/commit/c28683d)) docs: clean up oc.core public func docstrings - Russell Matney
- ([`5c86898`](https://github.com/russmatney/org-crud/commit/5c86898)) refactor: pulling more impl from oc.core into oc.parse - Russell Matney

  > Drying up the distance between the org parsing and building the node shape.

- ([`7a9f4bd`](https://github.com/russmatney/org-crud/commit/7a9f4bd)) refactor: move parse entrypoints to org-crud.parse namespace - Russell Matney
- ([`ba77007`](https://github.com/russmatney/org-crud/commit/ba77007)) chore: drop dead examples dir - Russell Matney

  > This might still be a viable path to an org-based blog, but i consider
  > it deprecated.

- ([`8e02c8f`](https://github.com/russmatney/org-crud/commit/8e02c8f)) feat: pull in organum rewrite, drop organum dep - Russell Matney

  > The organum dep org-crud was built on was rewritten to use instaparse -
  > i was hoping to do the same and drop the dep. This pulls in the rewrite
  > in a separate namespace. Next is dropping/melding these implementations
  > together and reducing/cleaning up/dedupling the work in
  > org-crud/node.clj.

- ([`da50b9b`](https://github.com/russmatney/org-crud/commit/da50b9b)) feat: run tests via babashka in ci - Russell Matney

### 26 Sep 2022

- ([`b25ff0a`](https://github.com/russmatney/org-crud/commit/b25ff0a)) chore: resolve clj-kondo errors - Russell Matney
- ([`ab1024f`](https://github.com/russmatney/org-crud/commit/ab1024f)) chore: fixed up actions workflow - Russell Matney
- ([`a164619`](https://github.com/russmatney/org-crud/commit/a164619)) build: specify java distribution - Russell Matney
- ([`9b75a36`](https://github.com/russmatney/org-crud/commit/9b75a36)) feat: update deps, tweak test - Russell Matney

### 17 Sep 2022

- ([`74f9ac6`](https://github.com/russmatney/org-crud/commit/74f9ac6)) fix: include links in headlines, not just body - Russell Matney
- ([`f6af86e`](https://github.com/russmatney/org-crud/commit/f6af86e)) chore: misc clean up/logs in markdown impl - Russell Matney
- ([`1e0d61a`](https://github.com/russmatney/org-crud/commit/1e0d61a)) fix: drop org props without names - Russell Matney

  > These sometimes get parsed out of comments in src blocks.

- ([`778fd22`](https://github.com/russmatney/org-crud/commit/778fd22)) fix: organum parsing lower-case begin/end blocks - Russell Matney
- ([`68668ee`](https://github.com/russmatney/org-crud/commit/68668ee)) wip: extend markdown org link with passed :fetch-item - Russell Matney

  > Support a function for fetching an item by uuid when parsing org-links.

- ([`9a64dd6`](https://github.com/russmatney/org-crud/commit/9a64dd6)) chore: drop clomacs dep - Russell Matney

  > Dropping this dep for now, as I'm not really using it.


### 16 Aug 2022

- ([`35abe97`](https://github.com/russmatney/org-crud/commit/35abe97)) feat: restore create/refile and tests - Russell Matney

  > Refactors the :add-item portion of the update flow to both add to a
  > matching org item's children _and_ append to the aggregated :items. This
  > is necessary after skip-children in item->lines. There's some
  > ambiguity/confusion between the flattened and nested item approaches
  > that could probably be simpler - for now we continue to support both.

- ([`aae24e4`](https://github.com/russmatney/org-crud/commit/aae24e4)) chore: add test artifacts to .gitignore - Russell Matney
- ([`1364dc3`](https://github.com/russmatney/org-crud/commit/1364dc3)) chore: delete gitignored test artifacts - Russell Matney
- ([`5534232`](https://github.com/russmatney/org-crud/commit/5534232)) feat: restore update feats, improve status reading/writing - Russell Matney

  > More statuses reading/writing properly.
  > 
  > Beware, other tests still failing!

- ([`4e5496f`](https://github.com/russmatney/org-crud/commit/4e5496f)) chore: pull in organum namespace, drop keyword parsing - Russell Matney

  > I didn't realize this lib was so small - will be dropping the dep and
  > refactoring at some point.
  > 
  > For now, TODO and DONE were being removed, but somehow not included in
  > the parsed obj. now we just use the org-crud keyword handling.

- ([`1eb7063`](https://github.com/russmatney/org-crud/commit/1eb7063)) chore: basic clj-kondo commands - Russell Matney
- ([`34b752a`](https://github.com/russmatney/org-crud/commit/34b752a)) fix: drop unsupported log usage - Russell Matney

  > Added this when working from clawe, but it's not a dep.


### 29 Jul 2022

- ([`a05e5f7`](https://github.com/russmatney/org-crud/commit/a05e5f7)) fix: dupe id handling, remove nil parent names - Russell Matney

### 28 Jul 2022

- ([`1458691`](https://github.com/russmatney/org-crud/commit/1458691)) feat: path->flattened-items now consumes path->nested-items - Russell Matney

  > Plus a fun lil tree-seq.
  > 
  > The result is: flattened items now include nested metadata, which is
  > particularly useful if you're getting the flattened items anyway.
  > 
  > This could be a bit slower - if perf is an issue, we should probably
  > fork and consume organum, then put more of this data together at o.g.
  > parse time, rather than the way i'm regexing for individual fields after
  > the fact.

- ([`eec7f0c`](https://github.com/russmatney/org-crud/commit/eec7f0c)) feat: setting :org/parent-ids - Russell Matney
- ([`6ac4c0d`](https://github.com/russmatney/org-crud/commit/6ac4c0d)) feat: attach relative index - Russell Matney

  > This plus the nested parent-name are useful to get to unique
  > fallback-ids when uuids are not included on org nodes

- ([`08f46bc`](https://github.com/russmatney/org-crud/commit/08f46bc)) feat: nested items setting :org/parent-name - Russell Matney

  > This nesting func is a little crazy.

- ([`aa08da1`](https://github.com/russmatney/org-crud/commit/aa08da1)) feat: parsing roam links to :org/links-to {:link/id <uuid>, :link/text <text>} - Russell Matney
- ([`6d4f81e`](https://github.com/russmatney/org-crud/commit/6d4f81e)) feat: ensure org/id across updates on root items - Russell Matney
- ([`1ef2010`](https://github.com/russmatney/org-crud/commit/1ef2010)) feat: update reading roam_tags, filetags, writing filetags - Russell Matney
- ([`67ae4a0`](https://github.com/russmatney/org-crud/commit/67ae4a0)) feat: restore update after #uuid fix - Russell Matney
- ([`7679e5a`](https://github.com/russmatney/org-crud/commit/7679e5a)) feat: ids as uuids, parsing filetags on root - Russell Matney
- ([`7b87a89`](https://github.com/russmatney/org-crud/commit/7b87a89)) fix: restore tests with misc fixes - Russell Matney

  > Drops support for some of the markdown namespace for now - not sure what
  > those tests are doing, but i don't think anyone is using this for a
  > jekyll or gatsby blog rn anyway.

- ([`2cce7e7`](https://github.com/russmatney/org-crud/commit/2cce7e7)) feat: restore update tests - Russell Matney

  > Scalars are now removed with an explicit :remove, not nil.

- ([`41d80a2`](https://github.com/russmatney/org-crud/commit/41d80a2)) refactor: drop org-crud.fs, switch to babashka.fs - Russell Matney
- ([`f647555`](https://github.com/russmatney/org-crud/commit/f647555)) refactor: rename 'org-crud.headline' to 'org-crud.node' - Russell Matney
- ([`3631dbf`](https://github.com/russmatney/org-crud/commit/3631dbf)) refactor: remove dynamic vars - Russell Matney

  > I never liked this api - this removes all dynamic vars. Some of these
  > were optional/flexible in a way that was
  > unnecessary (item->source-file), and others are better as a default. If
  > an org property is repeated in the same node, it will come out as a
  > list. It used to bug me that some items would have a scalar, others a
  > list, but it's dynamic and easy enough to deal with, plus it's quite
  > rare - i don't expect to use it. It came from a time that i was using
  > org as a database and needed more flexible metadata, but that's just
  > crazy, we're dropping that use-case.

- ([`a851e18`](https://github.com/russmatney/org-crud/commit/a851e18)) wip/misc: some lingering diffs - Russell Matney

### 1 May 2022

- ([`5f91343`](https://github.com/russmatney/org-crud/commit/5f91343)) feat: parse item priority - Russell Matney

### 30 Apr 2022

- ([`afdb44c`](https://github.com/russmatney/org-crud/commit/afdb44c)) feat: restore schedule, deadline, closed dates - Russell Matney

  > Pulls the dates through as strings for now. In the first build, these
  > were parsed and passed as instants or zoned-date-times via tick - that
  > had to be struct b/c of babashka compatibility.

- ([`33d6076`](https://github.com/russmatney/org-crud/commit/33d6076)) misc: restore old test - Russell Matney
- ([`815efa9`](https://github.com/russmatney/org-crud/commit/815efa9)) docs: delete .md readme, add bb.edn - Russell Matney

### 20 Mar 2022

- ([`8f0de4d`](https://github.com/russmatney/org-crud/commit/8f0de4d)) deps: update outdated - Russell Matney

### 6 Nov 2021

- ([`58182f7`](https://github.com/russmatney/org-crud/commit/58182f7)) fix: nil-punning and try-catching org/parse-file - Russell Matney

  > Hit a crash when an org file had an :END: without a :PROPERTIES: (which
  > had been deleted accidentally). This wraps the parse function in a try
  > catch so that we're not breaking everything when the org file can't be
  > parsed.


### 22 Aug 2021

- ([`0a600c4`](https://github.com/russmatney/org-crud/commit/0a600c4)) feat: port readme to org, org-roam dates-in-filename removal - Russell Matney

### 15 Feb 2021

- ([`6dd41ab`](https://github.com/russmatney/org-crud/commit/6dd41ab)) docs: todos, a doc string - Russell Matney

  > Capturing the current state of the global id helper.

- ([`b77350b`](https://github.com/russmatney/org-crud/commit/b77350b)) fix: don't add prop bucket unless there are props - Russell Matney
- ([`f46b00d`](https://github.com/russmatney/org-crud/commit/f46b00d)) fix: test for not adding property buckets working - Russell Matney
- ([`f4463b9`](https://github.com/russmatney/org-crud/commit/f4463b9)) wip: doctor auto-updating uuids - Russell Matney

  > Would be good to go were it not for issues in the update/parser
  > round-trip. Right now it adds property buckets to every item it writes,
  > requires the dynamic var magic to indicate org.property lists vs
  > scalars, and does not roundtrip logbook items at all.


### 14 Feb 2021

- ([`294e0ee`](https://github.com/russmatney/org-crud/commit/294e0ee)) todo: clocking out - Russell Matney
- ([`1cf5b8f`](https://github.com/russmatney/org-crud/commit/1cf5b8f)) misc: removes some dead repl code - Russell Matney

  > I used these to build up to this, but they didn't survive clean up.

- ([`ff33f0c`](https://github.com/russmatney/org-crud/commit/ff33f0c)) feat: doctor cli command prints duplciate uuids - Russell Matney

  > I recently broke parts of my org setup by refactoring some folders
  > together and combining things with roam - roam now does me the service
  > of throwing a huge error when trying to process some of my files,
  > because there are a large number of duplicated uuids in there. That part
  > is my fault, and it's an annoying problem to have, especially when it
  > means a big buffer printing a huge group of uuids and saying i thinks
  > that at least one is a duplicate.
  > 
  > This commit introduces a cli command 'doctor', whos first task is
  > analyzing your org-directory for problems, specifically duplicate uuids.
  > Duplicates are logged. If this is common or there are quite alot, maybe
  > we'll add some interactive support for updating the particular items,
  > 'Carve' style.


### 12 Feb 2021

- ([`45df048`](https://github.com/russmatney/org-crud/commit/45df048)) wip: rough sketch for doctor command - Russell Matney

### 7 Feb 2021

- ([`4ba893b`](https://github.com/russmatney/org-crud/commit/4ba893b)) feat: org-crud.counts ns for printing a report - Russell Matney

  > Right now this prints the number of level-one items, and average word
  > counts for those items. Could evolve toward a more general analysis of
  > the home dir's org-counts.


### 24 Nov 2020

- ([`e59fd42`](https://github.com/russmatney/org-crud/commit/e59fd42)) feat: support jekyll-style filenames - Russell Matney

  > Also refactors a few more shared helpers out to support simpler date and
  > file-basename access.

- ([`d2d17fb`](https://github.com/russmatney/org-crud/commit/d2d17fb)) fix: link-prefix, additional tags now supported as arguments - Russell Matney

  > Moves away from the default tag (note) and link-prefix (/notes).
  > 
  > Updates tests to support these as args and prevent them by default.

- ([`2aab895`](https://github.com/russmatney/org-crud/commit/2aab895)) feat: example for building a gatsby blog from an org-source dir - Russell Matney

  > I'll probably move these blog examples into their own repo soon - but
  > for now, just getting this out there.


### 23 Nov 2020

- ([`25f5057`](https://github.com/russmatney/org-crud/commit/25f5057)) wip: adds examples dir, threads opts through md namespace - Russell Matney

  > Not supporting jekyll yet, but it should be just a few functions from here.


### 21 Nov 2020

- ([`da00d0a`](https://github.com/russmatney/org-crud/commit/da00d0a)) docs: quick note for using as a clojure dep - Russell Matney
- ([`a4b4402`](https://github.com/russmatney/org-crud/commit/a4b4402)) fix: restore missing test files, fix broken tests - Russell Matney

  > These should not have been .gitignored - May be some cleaned up ones on
  > my other machine, but there're ready for clean up now.

- ([`017ad57`](https://github.com/russmatney/org-crud/commit/017ad57)) feat: ci running tests and linting - Russell Matney

  > Also restores tests by adding check. Not sure why that's now required,
  > but i'll probably use it anyway.


### 11 Nov 2020

- ([`a1201a7`](https://github.com/russmatney/org-crud/commit/a1201a7)) docs: update bb and clojure links - Russell Matney
- ([`2c407ee`](https://github.com/russmatney/org-crud/commit/2c407ee)) docs: include uberjar as 'release', update docs - Russell Matney
- ([`5ef1013`](https://github.com/russmatney/org-crud/commit/5ef1013)) docs: further document markdown conversion quirks - Russell Matney

  > Still some todos here, I'm afraid - not quite ready for general usage.

- ([`f0d0809`](https://github.com/russmatney/org-crud/commit/f0d0809)) fix: don't hide markdown fixtures from .gitignore - Russell Matney

  > Exposing these makes it easier to show just what is happening here.

- ([`dc4d6fd`](https://github.com/russmatney/org-crud/commit/dc4d6fd)) feat: document initial cli usage - Russell Matney

  > Hopefully building and distributing a self-contained binary isn't too
  > much of a reach from here.

- ([`70fadf2`](https://github.com/russmatney/org-crud/commit/70fadf2)) feat: org-crud.cli with org-to-markdown command - Russell Matney

  > A quick implementation of a cli file exposing a command for converting
  > a directory of org files into markdown files.
  > 
  > Can be run locally from the project root like:
  > 
  > bb -cp $(clojure -Spath) -m org-crud.cli org-to-markdown ~/Dropbox/notes tmp-out


### 8 Nov 2020

- ([`6238d56`](https://github.com/russmatney/org-crud/commit/6238d56)) docs: cli todo capture - Russell Matney

### 17 Sep 2020

- ([`d06b268`](https://github.com/russmatney/org-crud/commit/d06b268)) docs: fix model example - Russell Matney

### 16 Sep 2020

- ([`077bb00`](https://github.com/russmatney/org-crud/commit/077bb00)) feat: flattens org-props into item with org.prop namespace - Russell Matney

  > Updates tests to match. Breaks all consumers! glhf

- ([`8ab1680`](https://github.com/russmatney/org-crud/commit/8ab1680)) wip: namespaced org item keys - Russell Matney

  > Refactors org items to use namespaced keys.
  > 
  > Still remaining: flattening and prefixing props.
  > 
  > All tests passing for now. Some improvements made in places where test
  > fixtures were being depended on by multiple test files.
  > 
  > Also, kaocha watch is currently looping, b/c the tests write output to
  > files when run...

- ([`0ff7fc7`](https://github.com/russmatney/org-crud/commit/0ff7fc7)) docs: model docs, brief parsing/updating notes - Russell Matney

  > These changes have not yet been implemented!


### 14 Sep 2020

- ([`912deb8`](https://github.com/russmatney/org-crud/commit/912deb8)) fix: namespace deps - Russell Matney

### 10 Sep 2020

- ([`f38b45b`](https://github.com/russmatney/org-crud/commit/f38b45b)) fix: hack to allow private function usage - Russell Matney

### 8 Sep 2020

- ([`d82cb42`](https://github.com/russmatney/org-crud/commit/d82cb42)) fix: handling nil in ->word-count - Russell Matney

### 7 Sep 2020

- ([`2106104`](https://github.com/russmatney/org-crud/commit/2106104)) feat: attach urls parsed from title/body to items - Russell Matney

  > Convenient to have the item's urls at hand.

- ([`146aa3c`](https://github.com/russmatney/org-crud/commit/146aa3c)) feat: basic :word-count on every item - Russell Matney

  > Does not calc total word count for nested items - will have to be
  > aggregated elsewhere.

- ([`4f3f759`](https://github.com/russmatney/org-crud/commit/4f3f759)) feat: extend :recursive? support to update-dir-with-fn! - Russell Matney
- ([`9b1085a`](https://github.com/russmatney/org-crud/commit/9b1085a)) fix: handle roam_key return to snake_case - Russell Matney
- ([`da116c5`](https://github.com/russmatney/org-crud/commit/da116c5)) feat: support :recursive? opt - Russell Matney

  > For now only looks one dir down.


### 6 Sep 2020

- ([`3dc6ed2`](https://github.com/russmatney/org-crud/commit/3dc6ed2)) fix: prevent empty 'roam_tags' prop added to every file - Russell Matney
- ([`8f76d85`](https://github.com/russmatney/org-crud/commit/8f76d85)) feat: prop-bucket fix, helper for setting missing ids - Russell Matney

  > User-ns comment useful for updating a whole dir of org files with
  > missing ids.

- ([`6cc3de0`](https://github.com/russmatney/org-crud/commit/6cc3de0)) feat: update root items with update! api - Russell Matney

  > Some tweaks to get root items to re-write to file with the updated
  > props.

- ([`082c65e`](https://github.com/russmatney/org-crud/commit/082c65e)) fix: restore create tests - Russell Matney

  > These were failing b/c the fixture was dropped somehow.


### 5 Sep 2020

- ([`2eb1fef`](https://github.com/russmatney/org-crud/commit/2eb1fef)) Create LICENSE - Russell Matney

### 14 Aug 2020

- ([`8d219d7`](https://github.com/russmatney/org-crud/commit/8d219d7)) feat: support external links when converting to markdown - Russell Matney

  > The previous link solution was hard-coded to support internal links,
  > i.e. links to other notes. This updates the markdown conversion to
  > convert external links to a markdown format as well. Includes a quick
  > test.


### 6 Aug 2020

- ([`4a79a5b`](https://github.com/russmatney/org-crud/commit/4a79a5b)) feat: pull externally used functions to .api namespace - Russell Matney

  > This pulls functions that are called from yodo into a more dedicated
  > public namespace. TODO get doc strings on these.


### 5 Aug 2020

- ([`b199c3f`](https://github.com/russmatney/org-crud/commit/b199c3f)) feat: create-roam-file from item and dir - Russell Matney
- ([`57da6eb`](https://github.com/russmatney/org-crud/commit/57da6eb)) feat: read and write org/root items - Russell Matney

  > Allows for the creation of org files.
  > 
  > lines/item->root-lines can be used to get the content of a single org
  > page.
  > 
  > Not yet tested for nested items, but should work...?


### 4 Aug 2020

- ([`45bdd81`](https://github.com/russmatney/org-crud/commit/45bdd81)) refactor: pull delete, refile out of update - Russell Matney

  > Moving toward closer 'crud' alignment, and giving refile a place to
  > grow.

- ([`50c499b`](https://github.com/russmatney/org-crud/commit/50c499b)) refactor: pulls add funcs into new .create ns - Russell Matney

  > Breaks existing apis, for sure. Good luck out there!


### 23 Jul 2020

- ([`8ea87d5`](https://github.com/russmatney/org-crud/commit/8ea87d5)) fix: remove support for pulling ids from item names - Russell Matney

  > This was causing overwriting for journal headlines without ids.


### 22 Jul 2020

- ([`3d01005`](https://github.com/russmatney/org-crud/commit/3d01005)) feat: operate over an org file's items with a passed function - Russell Matney

  > A handy function that made it easy to add ids to org items missing them.


### 14 Jul 2020

- ([`1dc13a7`](https://github.com/russmatney/org-crud/commit/1dc13a7)) fix: allow unsetting of item status - Russell Matney

### 12 Jul 2020

- ([`cfad3db`](https://github.com/russmatney/org-crud/commit/cfad3db)) refactor: move (sadly hardcoded) 'garden' to 'notes' - Russell Matney

  > I'm chaning the naming for the digital garden stuff everywhere to
  > 'notes'. This detail should be pulled out of this repo!


### 10 Jul 2020

- ([`37f4a39`](https://github.com/russmatney/org-crud/commit/37f4a39)) feat: attach a :source-file to each item - Russell Matney

  > Useful for tracking the file to update when crudding. Notably does not
  > answer the question of where to put new items. Still relying on :type
  > for that in yodo.


### 7 Jul 2020

- ([`2e488d6`](https://github.com/russmatney/org-crud/commit/2e488d6)) fix: remove more date handling - Russell Matney

  > Pushing this out to consumers for now.
  > 
  > Would love to come back and support scheduled/deadline/clock-in/-out.
  > Will need a bb-compatible date library. could maybe just pass the raw
  > text out for now?

- ([`e4ae74e`](https://github.com/russmatney/org-crud/commit/e4ae74e)) fix: surface another dynamic var - Russell Matney

  > These dynamic vars let consumers inject handling for different prop
  > types. They need to be documented. For now i'm more focused on
  > collecting use-cases - these might just go away completely anyway.

- ([`7e3dcbc`](https://github.com/russmatney/org-crud/commit/7e3dcbc)) rm: clear out unused date functions - Russell Matney

  > These date funcs were not included to dodge the tick dependency, which
  > is not babaska compatible.

- ([`d2f6f9f`](https://github.com/russmatney/org-crud/commit/d2f6f9f)) fix: remove unused fs require - Russell Matney
- ([`042f7ba`](https://github.com/russmatney/org-crud/commit/042f7ba)) feat: parse-org-lines added back - Russell Matney

  > I found a few consumers of this after I'd too-aggressively deleted it.
  > It's useful to get non-org things (commits, draft journals, etc) into
  > the same shape as the org-items, which i'm using all over the app.


### 6 Jul 2020

- ([`c51cdba`](https://github.com/russmatney/org-crud/commit/c51cdba)) fix: readme typo - Russell Matney

  > Probably not the only one.


### 5 Jul 2020

- ([`38d68d5`](https://github.com/russmatney/org-crud/commit/38d68d5)) docs: write readme draft - Russell Matney

### 4 Jul 2020

- ([`bdfe0fb`](https://github.com/russmatney/org-crud/commit/bdfe0fb)) fix: handle two tildas in the same line - Russell Matney
- ([`cc0f651`](https://github.com/russmatney/org-crud/commit/cc0f651)) feat: exclude items tagged 'private' - Russell Matney

  > Links to these excluded pages will need to be removed/cleaned up. Maybe
  > gatsby will just do that for me?

- ([`1c579d2`](https://github.com/russmatney/org-crud/commit/1c579d2)) feat: support tilda -> tick conversion - Russell Matney
- ([`1c24b82`](https://github.com/russmatney/org-crud/commit/1c24b82)) feat: parse roam tags from root elems - Russell Matney

  > Also fixes the previous name bug - `[ ]` and friends were not being
  > parsed out.

- ([`01b8a59`](https://github.com/russmatney/org-crud/commit/01b8a59)) feat: quick date handling based on filename - Russell Matney

  > Following the default org-roam filename style for now.

- ([`92d58c4`](https://github.com/russmatney/org-crud/commit/92d58c4)) fix: add tags, hard-coded date, garden link prefix - Russell Matney

  > Right now i'm dumping into gatsby-dir/content/posts/garden/, so all the
  > links need a prefix for the relative link capture to work.
  > Should refactor this to be configurable at some point.

- ([`129e12a`](https://github.com/russmatney/org-crud/commit/129e12a)) fix: regex was removing the leading `[` in leading header links - Russell Matney

  > Hopefully this doesn't undo too much else. Much of this regex is not
  > relevant, and can probably be dropped (ex. the asterisks never come
  > through here anymore.)

- ([`04aae6b`](https://github.com/russmatney/org-crud/commit/04aae6b)) feat: appending backlinks to markdown files - Russell Matney
- ([`a493b4e`](https://github.com/russmatney/org-crud/commit/a493b4e)) feat: collecting links from a particular item - Russell Matney
- ([`dc7e69d`](https://github.com/russmatney/org-crud/commit/dc7e69d)) feat: convert org-links into md links - Russell Matney

  > This joins and re-splits the lines, which feels a bit odd. Not worrying
  > about it for now.

- ([`9b2caf8`](https://github.com/russmatney/org-crud/commit/9b2caf8)) wip: org links->md links helper working - Russell Matney

  > Now, to attach it to the process.

- ([`e9e455e`](https://github.com/russmatney/org-crud/commit/e9e455e)) feat: writing nested items, handling src-blocks - Russell Matney

  > Also disables the org-sections for now. They were useful at one point,
  > but can be quite noisey.

- ([`73b358b`](https://github.com/russmatney/org-crud/commit/73b358b)) test: basic frontmatter test - Russell Matney

  > Breaks tests apart a bit, seperating a general file count test from the
  > frontmatter specific test.

- ([`fa6bc25`](https://github.com/russmatney/org-crud/commit/fa6bc25)) feat: markdown ns consuming more api - Russell Matney

  > Setting a matching filename using the :source-name set by the parser.

- ([`0e34477`](https://github.com/russmatney/org-crud/commit/0e34477)) feat: initial markdown namespace - Russell Matney

  > Moving the markdown conversion into org-crud lets it apply elsewhere,
  > and provides a better dev experience than my current babashka setup.

- ([`dc6f6d6`](https://github.com/russmatney/org-crud/commit/dc6f6d6)) tweak: clear ported comment blocks - Russell Matney

  > Feels these are more misleading than helpful in the current form.


### 3 Jul 2020

- ([`471ce27`](https://github.com/russmatney/org-crud/commit/471ce27)) feat: pull fs functions from source - Russell Matney

  > Pulls the fs functions that I'm using, (and remove fs/file?) usage.

- ([`d315839`](https://github.com/russmatney/org-crud/commit/d315839)) wip: removes deps to get to bb portability - Russell Matney

  > Babashka is great but does not support everything, including
  > `clj-commons/fs` and `tick`. This wip removes deps that were crashing
  > babashka when org-crud was implemented. Tests failing, missing features.
  > First step is impling the fs functions. Tick should probably have never
  > been a direct dep, but parsing helpers should be configurable somehow,
  > maybe via more dynamism.

- ([`abe8d8f`](https://github.com/russmatney/org-crud/commit/abe8d8f)) fix: set dynamic var in test fixtures - Russell Matney
- ([`6de9492`](https://github.com/russmatney/org-crud/commit/6de9492)) fix: move tests.edn to proper location - Russell Matney
- ([`5ccec87`](https://github.com/russmatney/org-crud/commit/5ccec87)) fix: remove unused funcs, dead code - Russell Matney
- ([`1c3c3c0`](https://github.com/russmatney/org-crud/commit/1c3c3c0)) feat: impl multi-prop-keys as dynamic var - Russell Matney
- ([`5fa8123`](https://github.com/russmatney/org-crud/commit/5fa8123)) fix: clean up .core ns, better parse fn names - Russell Matney

  > Going with path->flattened-items and path->nested-items.

- ([`d60539f`](https://github.com/russmatney/org-crud/commit/d60539f)) wip: tests passing, but a few things hard-coded - Russell Matney

  > Will need to make multi-prop-keys configurable, probably via a passed
  > options object.

- ([`aedb91c`](https://github.com/russmatney/org-crud/commit/aedb91c)) wip: headline tests passing - Russell Matney

  > Hardcoded a :repo-ids key for now.
