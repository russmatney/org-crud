(ns org-crud.headline-test
  (:require
   [org-crud.headline :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [org-crud.core :as org]
   [org-crud.fs :as fs]
   [clojure.set :as set]))

(def fixture-dir (str fs/*cwd* "/test/org_crud"))

(defn parsed-org-file [fname]
  (org/path->nested-item (str fixture-dir "/" fname)))

(defn test-fixtures
  [f]
  (binding [sut/*multi-prop-keys* #{:repo-ids}]
    (f)))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parse, headline helper unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ->name-test
  (testing "parsing names from todo headlines"
    (is (= "01 test todo" (sut/->name {:name "01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "[ ] 01 test todo" :type :section})))
    (is (= "01 test todo" (sut/->name {:name "[X] 01 test todo" :type :section})))
    (is (= "test todo" (sut/->name {:name "test todo" :type :section})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; date parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO restore date features (and mind the bb compatibility)
;; (deftest ->dates-test
;;   (let [d (util/date->ny-zdt (t/date "2020-03-13"))]
;;     (testing "date parsing"
;;       (testing "scheduled"
;;         (is (= d
;;                (:scheduled
;;                 (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri>")))))
;;       (testing "deadline"
;;         (is (= d
;;                (:deadline
;;                 (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri>")))))
;;       (testing "scheduled AND deadline"
;;         (is (= {:scheduled d :deadline d}
;;                (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri> DEADLINE: <2020-03-13 Fri>")))
;;         (is (= {:scheduled d :deadline d}
;;                (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri> SCHEDULED: <2020-03-13 Fri>")))))))

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

;; (deftest ->properties-test
;;   (testing "parses property buckets into reasonable props"
;;     (let [hl   (sut/->properties example-headline)
;;           date (util/date->ny-zdt "2019-04-07 Sun 10:23")]
;;       (is (= date (:archive-time hl))))))

(deftest ->properties-test-multi-value
  (testing "parses property buckets into lists when applicable"
    (let [hl (sut/->properties example-headline)]
      (is (set/subset? #{"my/repo" "my/other-repo"} (set (:repo-ids hl)))))))

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
