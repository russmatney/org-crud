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
      (is (= #{{:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                :link/text "links"}
               {:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                :link/text "across lines"}}
             (set (:org/links-to item)))))

    (testing "child node roam links"
      (is (= #{{:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                :link/text "links"}
               {:link/id   #uuid "910e0d6e-759d-4a9b-809c-78a6a0b6538b"
                :link/text "sometimes multiple times"}}
             (-> "child with links" item-with-name :org/links-to set))))))

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

    (testing ":org/images parses name, caption, path, extension"
      (let [it (item-with-name "blog supporting")]
        (is it)
        (is (-> it :org/images vec first))
        (let [img (-> it :org/images vec first)]
          (is (= (:image/name img) "gameplay recording from HatBot"))
          (is (= (:image/caption img) "Some clip or other"))
          (is (= (:image/path img) "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))
          (is (:image/path-expanded img))
          (is (= (:image/path-expanded img)
                 (str (fs/expand-home "~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4"))))
          (is (= (:image/extension img) "mp4"))
          (is (= (:image/date-string img) "2023-03-13 09:30")))))))
