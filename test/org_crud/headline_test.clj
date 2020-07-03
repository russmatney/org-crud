(ns org-crud.headline-test
  (:require
   [org-crud.headline :as sut]
   [clojure.test :refer [deftest testing is]]
   [org-crud.util :as util]
   [tick.alpha.api :as t]
   [clojure.set :as set]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parse, headline helper unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ->id-test
  (testing "parsing ids from todo headlines"
    (is (= "01" (sut/->id {:name "01"})))
    (is (= "01" (sut/->id {:name "01 test todo"})))
    (is (= "01" (sut/->id {:name "[ ] 01 test todo"})))
    (is (= "01" (sut/->id {:name "[X] 01 test todo"})))
    (is (= nil (sut/->id {:name "test todo"})))))

(deftest ->name-test
  (testing "parsing names from todo headlines"
    (is (= "" (sut/->name {:name "01"})))
    (is (= "test todo" (sut/->name {:name "01 test todo"})))
    (is (= "test todo" (sut/->name {:name "[ ] 01 test todo"})))
    (is (= "test todo" (sut/->name {:name "[X] 01 test todo"})))
    (is (= "test todo" (sut/->name {:name "test todo"})))))

(deftest has-number?-test
  (testing "parsing names from todo headlines"
    (is (= true (sut/has-number? {:name "01"})))
    (is (= true (sut/has-number? {:name "01 test todo"})))
    (is (= true (sut/has-number? {:name "[ ] 01 test todo"})))
    (is (= true (sut/has-number? {:name " [ ] 01 test todo"})))
    (is (= true (sut/has-number? {:name "[X] 01 test todo"})))
    (is (= false (sut/has-number? {:name "test todo"})))))

(deftest has-TODO?-test
  (testing "parsing names from todo headlines"
    (is (= false (sut/has-TODO? {:name "01"})))
    (is (= false (sut/has-TODO? {:name "01 test todo"})))
    (is (= false (sut/has-TODO? {:name "test todo"})))
    (testing "with bracket TODO blocks"
      (is (= true (sut/has-TODO? {:name "[ ] 01 test todo"})))
      (is (= true (sut/has-TODO? {:name " [ ] 01 test todo"})))
      (is (= true (sut/has-TODO? {:name "[X] 01 test todo"}))))
    (testing "with TODO and DONE keywords"
      (is (= true (sut/has-TODO? {:name "TODO 01 test todo"})))
      (is (= true (sut/has-TODO? {:name "DONE 01 test todo"}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; date parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ->dates-test
  (let [d (util/date->ny-zdt (t/date "2020-03-13"))]
    (testing "date parsing"
      (testing "scheduled"
        (is (= d
               (:scheduled
                (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri>")))))
      (testing "deadline"
        (is (= d
               (:deadline
                (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri>")))))
      (testing "scheduled AND deadline"
        (is (= {:scheduled d :deadline d}
               (sut/metadata->date-map "SCHEDULED: <2020-03-13 Fri> DEADLINE: <2020-03-13 Fri>")))
        (is (= {:scheduled d :deadline d}
               (sut/metadata->date-map "DEADLINE: <2020-03-13 Fri> SCHEDULED: <2020-03-13 Fri>")))))))

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
  (testing "parses property buckets into reasonable props"
    (let [hl   (sut/->properties example-headline)
          date (util/date->ny-zdt "2019-04-07 Sun 10:23")]
      (is (= date (:archive-time hl))))))

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
     :text      "https://www.principles.com/"}
    {:line-type :table-row
     :text      "https://github.com/gothinkster/clojurescript-reframe-realworld-example-app"}]
   :name (str "[X] name " name-url)})

(deftest ->urls-test
  (let [urls (set (sut/->urls url-headline))]
    (testing "parses urls from headline name"
      (is (contains? urls name-url)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Body (and source block) parsing
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; There is a fine data structure from the org-parser.
;; Lets just use it.
;; If you want strings, just filter :blank :line-types and map :text
;;
;; This was a wip foor the ->body func
;; (reduce
;;   (fn [db line]
;;     (let [table-rows (:table-rows db)
;;           line-type  (:line-type line)]
;;       (case line-type
;;         :table-row
;;         (-> db
;;             (update :table-rows conj (:text line)))

;;         :blank
;;         (-> db
;;             (assoc :table-rows [])
;;             (update :body conj table-rows))

;;         :block
;;         (when (= (:block-type line) "SRC")
;;           (-> db
;;               (assoc :table-rows [])
;;               (update :body conj table-rows)
;;               (update :body conj {})
;;               )

;;           )))
;;     db)
;;   {:body       []
;;    :table-rows []}
;;   content)

;; Left here for posterity, right now this is a pass-through
(def body-headline
  {:content
   [{:line-type :table-row
     :text      "Host: algo"}
    {:line-type :table-row
     :text      "L: D33J: Park (Tape Version)"}
    {:line-type :table-row
     :text      "M: source block example"}
    {:line-type :blank
     :text      ""}
    {:line-type :table-row
     :text      "example:"}
    {:type       :block
     :content
     [{:line-type :table-row
       :text      "(defn foo []"}
      {:line-type :table-row
       :text      "  \"hi\")"}]
     :block-type "SRC"
     :qualifier  "clojure"}
    {:line-type :table-row
     :text      "end"}]})

;; Left here for posterity, right now this is a pass-through

;; TODO write a test and then auto-detect skipping the first paragraph
(deftest ->body-test
  (let [_body (sut/->body body-headline)]
    (testing "parses a body from a headline"
      (is true))))
