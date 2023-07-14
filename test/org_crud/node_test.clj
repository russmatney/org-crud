(ns org-crud.node-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [babashka.fs :as fs]
   [clojure.set :as set]
   [clojure.string :as string]

   [org-crud.node :as sut]
   [org-crud.core :as org]))

(def fixture-dir (str (fs/cwd) "/test/org_crud"))

(defn parsed-org-file [fname]
  (org/path->nested-item (str fixture-dir "/" fname)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parse, headline helper unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ->name-test
  (testing "parsing names from todo headlines"
    (is (= "01 test todo" (sut/->name {:name "01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "[ ] 01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "[X] 01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "DONE 01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "SKIP 01 test todo" :type :section})))
    (is (= "test todo" (sut/->name {:name "test todo" :type :section})))))

(deftest ->name-string-test
  (testing "removes org-links"
    (is (= "test todo" (sut/->name-string {:name "[ ] test todo" :type :section})))
    (is (= "test todo" (sut/->name-string {:name "test todo" :type :section})))
    (is (= "test todo" (sut/->name-string {:name "test [[id:some-id][todo]]" :type :section})))
    (is (= "test with inner text todo" (sut/->name-string {:name "test [[id:some-id][with inner]] text [[id:other-id][todo]]" :type :section})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; date parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ->dates-test
  (let [d "2020-03-13 Fri"]
    (testing "date parsing"
      (testing "scheduled"
        (is (= {:org/scheduled d} (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri>"))))
      (testing "deadline"
        (is (= {:org/deadline d} (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri>"))))
      (testing "scheduled AND deadline"
        (is (= {:org/scheduled d :org/deadline d}
               (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri> DEADLINE: <2020-03-13 Fri>")))
        (is (= {:org/scheduled d :org/deadline d}
               (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri> SCHEDULED: <2020-03-13 Fri>"))))
      (testing "closed"
        (is (= {:org/closed d} (sut/metadata->date-map "CLOSED: <2020-03-13 Fri>"))))

      (testing "all uh dem"
        (is (= {:org/closed    "2022-04-30 Sat 17:42"
                :org/deadline  "2022-04-30 Sat"
                :org/scheduled "2022-04-30 Sat"}
               (sut/metadata->date-map
                 "CLOSED: [2022-04-30 Sat 17:42] DEADLINE: <2022-04-30 Sat> SCHEDULED: <2022-04-30 Sat>")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Property drawers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def example-headline
  {:type :section
   :content
   [{:line-type :metadata
     :text      "DEADLINE: <2019-04-01 Mon>"}
    {:type :drawer
     :content
     [{:line-type :property-drawer-item
       :text      ":ARCHIVE_TIME: 2019-04-07 Sun 10:23"}
      {:line-type :property-drawer-item
       :text      ":ARCHIVE_FILE: ~/Dropbox/todo/todo.org"}
      {:line-type :property-drawer-item
       :text      ":ARCHIVE_OLPATH: 2019-04-01"}
      {:line-type :property-drawer-item
       :text      ":ARCHIVE_CATEGORY: todo"}
      {:line-type :property-drawer-item
       :text      ":ARCHIVE_TODO: [X]"}
      {:line-type :property-drawer-item
       :text      ":repo_ids: my/repo"}
      {:line-type :property-drawer-item
       :text      ":repo_ids+: my/other-repo"}]}]
   :name "[X] create cards"})

(deftest ->properties-test
  (testing "parses property drawers into `org.prop`/s"
    (let [hl   (sut/->properties example-headline)
          date "2019-04-07 Sun 10:23"]
      (is (= date (:org.prop/archive-time hl))))))

(deftest ->properties-test-multi-value
  (testing "parses property buckets into lists when applicable"
    (let [hl (sut/->properties example-headline)]
      (is (set/subset? #{"my/repo" "my/other-repo"} (set (:org.prop/repo-ids hl)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def name-url "http://name-url.com")
(def url-headline
  {:type :section
   :content
   [{:line-type :table-row
     :text      "some context https://www.principles.com/"}
    {:line-type :table-row
     :text      "https://github.com/gothinkster/clojurescript-reframe-realworld-example-app or other"}]
   :name (str "[X] name " name-url)})

(deftest ->urls-test
  (let [urls (set (sut/->urls url-headline))]
    (testing "parses urls from headline name"
      (is (= 3 (count urls)))
      (is (contains? urls name-url)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest roam-tags-test
  (let [item (parsed-org-file "core-test.org")]
    (is (set/subset? #{"roam" "tags" "like" "this"}
                     (set (:org/tags item))))))

(deftest parses-filetags
  (let [item (parsed-org-file "node-test.org")]
    (is (= #{"somefiletag" "post"} (:org/tags item)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; level
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (->>
    (parsed-org-file "node-test.org")
    (tree-seq (comp seq :org/items) :org/items)))

(deftest level-test
  (let [item   (parsed-org-file "node-test.org")
        sorted (->> item (tree-seq (comp seq :org/items) :org/items) (sort-by :org/level-int))]
    (is (->> sorted (map :org/level-int) (remove int?) empty?))
    (is (= (count sorted)
           (->> sorted (map :org/level-int) (filter int?) count)))

    (let [less-than-0 (->> sorted (map :org/level-int) (filter #(< % 0)))
          more-than-6 (->> sorted (map :org/level-int) (filter #(> % 6)))]
      (is (empty? less-than-0))
      (is (empty? more-than-6)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; priority
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest priority-test
  (let [item        (parsed-org-file "node-test.org")
        prioritized (->> item :org/items (filter (comp #(string/includes? % "prioritized")
                                                       :org/name)))
        sorted      (->> prioritized (sort-by :org/priority))]
    (is (= 4 (count sorted)))
    (let [[one two three four] (map :org/priority sorted)]
      (is (= "A" one))
      (is (= "B" two))
      (is (= "C" three))
      (is (= "C" four)))

    ;; make sure the names don't include the priority
    (let [names (map :org/name sorted)]
      (is (= 0 (->> names
                    (filter
                      (fn [n]
                        (or
                          (string/includes? n "#A")
                          (string/includes? n "#B")
                          (string/includes? n "#C"))))
                    count))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ids/uuids
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parses-ids-from-buckets
  (let [item       (parsed-org-file "node-test.org")
        matches-id (fn [it id]
                     (is (= (:org/id it) (java.util.UUID/fromString id))))]

    (testing "root id"
      (matches-id item "109f0706-9de3-426e-a63d-3ab2fd0d107d"))

    (testing "children ids"
      (doall
        (for [item (:org/items item)]
          (cond
            (#{"with an id"} (:org/name item))
            (matches-id item "2c96a967-7b44-4e4c-8577-947640c03ae8")

            (#{"with a spaced out property bucket"} (:org/name item))
            (matches-id item "86af07dc-4cc2-47b4-8113-2cd2b4c9c9ba")

            :else nil))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest node-links-to
  (let [item           (parsed-org-file "node-test.org")
        item-with-name (fn [n]
                         (some->> item :org/items (filter (comp #{n} :org/name)) first))]

    (testing "root node roam links"
      (let [[a b] (:org/links-to item)]
        (is (= a {:link/id      #uuid "01839801-01a5-4ca9-ad2b-d4b9e122be14"
                  :link/text    "across lines"
                  :link/context "This is my test node, sometimes with [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]].
Sometimes these links break across lines likeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee [[id:01839801-01a5-4ca9-ad2b-d4b9e122be14][across
lines]]
[[~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4]]",
                  :link/matching-lines
                  ["Sometimes these links break across lines likeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee [[id:01839801-01a5-4ca9-ad2b-d4b9e122be14][across"]}))

        (is (= b {:link/id             #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                  :link/text           "links"
                  :link/context        "This is my test node, sometimes with [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]].
Sometimes these links break across lines likeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee [[id:01839801-01a5-4ca9-ad2b-d4b9e122be14][across
lines]]
[[~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4]]",
                  :link/matching-lines ["This is my test node, sometimes with [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]]."]}))))

    (testing "child node roam links"
      (let [[a b] (-> "child with links" item-with-name :org/links-to)]
        (is (= a {:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                  :link/text "sometimes multiple times"
                  :link/context
                  "children nodes have [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]] too
they can link to the same, [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][sometimes multiple times]]",
                  :link/matching-lines
                  ["children nodes have [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]] too"
                   "they can link to the same, [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][sometimes multiple times]]"]}))

        (is (= b {:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                  :link/text "links"
                  :link/context
                  "children nodes have [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]] too
they can link to the same, [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][sometimes multiple times]]",
                  :link/matching-lines
                  ["children nodes have [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][links]] too"
                   "they can link to the same, [[id:910e0d6e-759d-4a9b-809c-78a6a0b6538b][sometimes multiple times]]"]}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; images
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest node-images
  (let [item           (parsed-org-file "node-test.org")
        item-with-name (fn [n]
                         (some->> item :org/items
                                  (filter (comp #(string/includes? % n)
                                                :org/name-string))
                                  first))]

    (testing ":org/images parses top-level images without issue"
      (is item)
      (let [img (-> item :org/images first)]
        (is (= (:image/name img) "top-level images work great"))
        (is (= (:image/caption img) "Some clip or other"))
        (is (= (:image/path img) "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))
        (is (:image/path-expanded img))
        (is (= (:image/path-expanded img)
               (str (fs/expand-home "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))))
        (is (= (:image/extension img) "mp4"))
        (is (= (:image/date-string img) "2023-03-13 09:30"))))

    (testing ":org/images parses name, caption, path, extension"
      (let [it (item-with-name "blog supporting")]
        (is it)
        (is (-> it :org/images first))
        (let [img (-> it :org/images first)]
          (is (= (:image/name img) "gameplay recording from HatBot"))
          (is (= (:image/caption img) "Some clip or other"))
          (is (= (:image/path img) "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))
          (is (:image/path-expanded img))
          (is (= (:image/path-expanded img)
                 (str (fs/expand-home "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))))
          (is (= (:image/extension img) "mp4"))
          (is (= (:image/date-string img) "2023-03-13 09:30")))))

    (testing ":org/images parses multiple images in one item"
      (let [it (item-with-name "multiple images parse")]
        (is it)
        (is (-> it :org/images first))
        (let [[img1 img2 img3] (-> it :org/images vec)]
          (is (= (:image/name img1) "gameplay recording from HatBot"))
          (is (= (:image/caption img1) "Some clip or other"))
          (is (= (:image/path img1) "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))
          (is (= (:image/name img2) "some rando screenshot"))
          (is (= (:image/caption img2) "that you can't wait to see"))
          (is (= (:image/path img2) "~/Screenshots/screenshot_2023-04-03_12:10:59-0400.jpg"))
          (is (= (:image/name img3) "some screenshot that doesn't exist"))
          (is (= (:image/caption img3) "that you'll never see"))
          (is (= (:image/path img3) "~/Screenshots/screenshot_i_dont_exist.jpg")))))

    (testing ":org/images does not get invoked for weird-image-like roam links"
      (let [it (item-with-name "not an image path")]
        (is it)
        (is (= [] (-> it :org/images vec)))))

    (testing ":org/images handles text immediately preceding an image name"
      (let [it (item-with-name "image path with text immediately preceding it")]
        (is it)
        (is (-> it :org/images first))
        (let [img (-> it :org/images first)]
          (is (= (:image/name img) "screenshot of godot dock position popup"))
          (is (= (:image/caption img) "click and drag like a window manager"))
          (is (= (:image/path img) "~/Screenshots/screenshot_2023-02-03_22:38:44-0500.jpg")))))))
