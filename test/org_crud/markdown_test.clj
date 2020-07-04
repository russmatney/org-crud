(ns org-crud.markdown-test
  (:require
   [org-crud.markdown :as sut]
   [clojure.test :refer [deftest testing is]]
   [org-crud.core :as org]
   [org-crud.fs :as fs]
   [clojure.string :as string]))

(def fixture-dir (str fs/*cwd* "/test/org_crud/markdown_fixtures"))

(defn parsed-org-files []
  (org/dir->nested-items fixture-dir))

(defn parsed-org-file [fname]
  (org/path->nested-item (str fixture-dir "/" fname)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; general
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest build-and-write-count-test
  (doall (sut/org-dir->md-dir fixture-dir fixture-dir))
  (let [files (fs/list-dir fixture-dir)]
    (testing "same number of org and md files"
      (is (> (->> files (filter #(= (fs/extension %) ".org")) count) 0))
      (is (= (->> files (filter #(= (fs/extension %) ".org")) count)
             (->> files (filter #(= (fs/extension %) ".md")) count))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest frontmatter-test
  (let [example-org (parsed-org-file "example.org")
        lines       (-> example-org sut/item->frontmatter)]
    (testing "org items convert to a proper frontmatter"
      (is (= "---" (first lines)))
      (is (= "---" (last lines)))
      (is (contains? (set lines) "title: Example Org File"))
      (is (contains? (set lines) "tags:"))
      (is (contains? (set lines) "  - garden"))
      )))

(deftest frontmatter-test-dates
  (let [example-org (parsed-org-file "20200618104339-dated-example.org")
        lines       (-> example-org sut/item->frontmatter)]
    (testing "org items convert to a proper frontmatter"
      (is (contains? (set lines) "date: 2020-06-18")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->md-body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest markdown-body-comment-test
  (let [example-org (parsed-org-file "example.org")
        lines       (-> example-org sut/item->md-body)]
    (testing "drops org comments"
      (is (= () (->> lines (filter #(string/starts-with? % "#+"))))))))

(def example-item
  {:level :root,
   :name  "Example Org File",
   :body  [{:line-type :comment, :text "#+TITLE: Example Org File"}
           {:line-type :blank, :text ""}
           {:line-type :table-row, :text "Some org content."}
           {:line-type :blank, :text ""}],
   :items
   [{:name  "An org header",
     :level 1,
     :items
     [{:level 2,
       :name  "A nested org header",
       :body
       [{:line-type :table-row,
         :text      "Content therein."}],}]
     :body  []}
    {:level 1,
     :name  "Conclusion",
     :body  []}]})

(deftest markdown-body-test
  (let [example-org example-item
        lines       (->> example-org
                         sut/item->md-body
                         (remove empty?))]
    (testing "converts items to headers based on level"
      (is (= "# An org header" (some->> lines
                                        (filter #(string/starts-with? % "#"))
                                        first)))
      (is (= "## A nested org header"
             (some->> lines
                      (filter #(string/starts-with? % "##"))
                      first)))
      (is (= "# Conclusion" (some->> lines
                                     (filter #(string/starts-with? % "#"))
                                     last)))
      (is (= "Some org content." (some->> lines
                                          (remove #(string/starts-with? % "#"))
                                          first)))
      (is (= "Content therein." (some->> lines
                                         (remove #(string/starts-with? % "#"))
                                         last))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; src blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def example-item-src-block
  {:level :root,
   :name  "Example Org File",
   :body  [{:line-type :comment, :text "#+TITLE: Example Org File"}
           {:line-type :blank, :text ""}]
   :items
   [{:name  "A src-block org header",
     :level 1,
     :body
     [{:type       :block,
       :content
       [{:line-type :table-row, :text "(-> \"hello\""}
        {:line-type :table-row, :text "    (println \"world\"))"}],
       :block-type "SRC",
       :qualifier  "clojure"}],}]})

(deftest markdown-code-block-test
  (let [example-org example-item-src-block
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "builds markdown source blocks"
      (is (= "``` clojure" (->> lines
                                (filter #(string/starts-with? % "```"))
                                first)))
      (is (= "```" (->> lines
                        (filter #(string/starts-with? % "```"))
                        last))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; lists
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def example-org-with-list
  {:level :root,
   :name  "Example Org File",
   :body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :items
   [{:level 1,
     :name  "content with a link",
     :body
     [{:line-type :table-row, :text "It's focuses are:"}
      {:line-type :unordered-list, :text "- inbox processing"}
      {:line-type :unordered-list, :text "- daily planning"}],}]})

(deftest markdown-with-list-test
  (let [example-org example-org-with-list
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    ;; TODO we may need newlines before and after lists
    (testing "includes unordered lists"
      (is (= "- inbox processing" (->> lines
                                       (filter #(string/starts-with? % "-"))
                                       first))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def example-item-with-link
  {:level :root,
   :name  "Example Org File",
   :body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :items
   [{:level 1,
     :name  "content with a link",
     :body
     [{:line-type :unordered-list,
       :text
       "- Wide net for [[file:20200609220548-capture_should_be_easy.org][easy capture]]"}]}]})

(deftest markdown-with-link-test-conversion
  (let [example-org example-item-with-link
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "includes markdown-style links"
      (is (= "- Wide net for [easy capture](/garden/20200609220548-capture_should_be_easy)"
             (->> lines
                  (filter #(string/starts-with? % "- Wide"))
                  first))))))

(deftest markdown-with-link-test-links-func
  (let [example-org example-item-with-link
        links       (->> example-org sut/item->links)]
    (testing "includes markdown-style links"
      (is (= {:name "easy capture"
              :link "20200609220548-capture_should_be_easy"}
             (first links))))))

(def example-item-with-line-broken-link
  {:level :root,
   :name  "Example Org File",
   :body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :items
   [{:level 1,
     :name  "content with a link",
     :body
     [{:line-type :unordered-list,
       :text
       "- Wide net for [[file:20200609220548-capture_should_be_easy.org][easy"}
      {:line-type :table-row, :text "  capture]]"}]}]})

(deftest markdown-with-link-test-line-break
  (let [example-org example-item-with-line-broken-link
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "includes markdown-style links"
      (is (= ["- Wide net for [easy"
              "  capture](/garden/20200609220548-capture_should_be_easy)"]
             (->> lines (drop 1)))))))

(def example-header-link
  {:level :root,
   :name  "yodo, the pitch and demo outline",
   :body
   [{:line-type :comment, :text "#+TITLE: yodo, the pitch and demo outline"}]
   :items
   [{:level 1,
     :name
     "[[file:link-name.org][text name]] blah"}]})

(deftest markdown-with-link-in-header-test
  (let [example-org example-header-link
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "includes markdown-style links"
      (is (contains? (set lines)
                     "# [text name](/garden/link-name) blah")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; test rn by example - check that they show up in fixture-dir/example.md
