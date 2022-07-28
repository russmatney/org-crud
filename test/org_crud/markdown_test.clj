(ns org-crud.markdown-test
  (:require
   [org-crud.markdown :as sut]
   [clojure.test :refer [deftest testing is]]
   [org-crud.core :as org]
   [babashka.fs :as fs]
   [clojure.string :as string]))

(def fixture-dir (str (fs/cwd) "/test/org_crud/markdown_fixtures"))

(defn reset-fixture-dir []
  (let [files (fs/list-dir fixture-dir)]
    (doall
      (->> files
           (filter #(= (fs/extension %) ".md"))
           (map fs/delete)))))

(defn parsed-org-files []
  (org/dir->nested-items fixture-dir))

(defn parsed-org-file [fname]
  (org/path->nested-item (str fixture-dir "/" fname)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; general
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest build-and-write-count-test
  (let [
        test-fn
        (fn []
          (let [files (fs/list-dir fixture-dir)]
            (is (> (->> files (filter #(= (fs/extension %) ".org")) count) 0))
            (is (= (->> files (filter #(= (fs/extension %) ".org")) count)
                   (->> files (filter #(= (fs/extension %) ".md")) count)))))]
    (testing "same number of org and md files"
      (reset-fixture-dir)
      (sut/org-dir->md-dir fixture-dir fixture-dir)
      (test-fn))
    (testing "overall success for gatsby blog type"
      (reset-fixture-dir)
      (sut/org-dir->md-dir fixture-dir fixture-dir {:blog-type "gatsby"})
      (test-fn))
    (testing "overall success for jekyll blog type"
      (reset-fixture-dir)
      (sut/org-dir->md-dir fixture-dir fixture-dir {:blog-type "jekyll"})
      (test-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->md-filename
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest markdown#md-filename
  (testing "default to gatsby style filenames"
    (let [example-org (parsed-org-file "20200618104339-dated-example.org")
          fname       (-> example-org sut/item->md-filename)]
      (is fname)
      (is (= fname "20200618104339-dated-example.md"))))
  (testing "and if gatsby is specified"
    (let [example-org (parsed-org-file "20200618104339-dated-example.org")
          fname       (-> example-org (sut/item->md-filename {:blog-type "gatsby"}))]
      (is fname)
      (is (= fname "20200618104339-dated-example.md"))))
  (testing "sets a jekyll filename format"
    (let [example-org (parsed-org-file "20200618104339-dated-example.org")
          fname       (-> example-org (sut/item->md-filename {:blog-type "jekyll"}))]
      (is fname)
      (is (= fname "2020-06-18-dated-example.md")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest frontmatter-test
  (let [example-org (parsed-org-file "example.org")
        lines       (-> example-org sut/item->frontmatter)]
    (testing "org items convert to a proper frontmatter"
      (is (= "---" (first lines)))
      (is (= "---" (last lines)))
      (is (contains? (set lines) "title: \"Example Org File\"")))))

(deftest frontmatter-test-dates
  (let [example-org (parsed-org-file "20200618104339-dated-example.org")
        lines       (-> example-org sut/item->frontmatter)]
    (testing "org items convert to a proper frontmatter"
      (is (contains? (set lines) "date: 2020-06-18")))))

(deftest frontmatter-test-tags
  (testing "no default tags"
    (let [example-org (parsed-org-file "example.org")
          lines       (-> example-org
                          (sut/item->frontmatter)
                          (->> (map string/trim)))]
      (is (not (contains? (set lines) "tags:")))))

  (testing "set passed tags"
    (let [example-org (parsed-org-file "example.org")
          lines       (-> example-org
                          (sut/item->frontmatter {:tags #{"note"
                                                          "sometag"}})
                          (->> (map string/trim)))]
      (testing "org items convert to a proper frontmatter"
        (is (contains? (set lines) "tags:"))
        (is (contains? (set lines) "- note"))
        (is (contains? (set lines) "- sometag")))))

  (testing "include passed and roam_tags"
    (let [example-org (parsed-org-file "20200618104339-with-roam-tags.org")
          lines       (-> example-org
                          (sut/item->frontmatter {:tags #{"note" "sometag"}})
                          (->> (map string/trim)))]
      (testing "org items convert to a proper frontmatter"
        (is (contains? (set lines) "tags:"))
        (is (contains? (set lines) "- dated"))
        (is (contains? (set lines) "- someothertag"))
        (is (contains? (set lines) "- note"))
        (is (contains? (set lines) "- sometag"))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->md-body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest markdown-body-comment-test
  (let [example-org (parsed-org-file "example.org")
        lines       (-> example-org sut/item->md-body)]
    (testing "drops org comments"
      (is (= () (->> lines (filter #(string/starts-with? % "#+"))))))))

(def example-item
  {:org/level :level/root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}
               {:line-type :blank, :text ""}
               {:line-type :table-row, :text "Some org content."}
               {:line-type :blank, :text ""}],
   :org/items
   [{:org/name  "An org header",
     :org/level 1,
     :org/items
     [{:org/level 2,
       :org/name  "A nested org header",
       :org/body
       [{:line-type :table-row,
         :text      "Content therein."}],}]
     :org/body  []}
    {:org/level 1,
     :org/name  "Conclusion",
     :org/body  []}]})

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
  {:org/level :level/root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}
               {:line-type :blank, :text ""}]
   :org/items
   [{:org/name  "A src-block org header",
     :org/level 1,
     :org/body
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
  {:org/level :level/root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :org/items
   [{:org/level 1,
     :org/name  "content without a link",
     :org/body
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
;; internal links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def example-item-with-link
  {:org/level :level/root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :org/items
   [{:org/level 1,
     :org/name  "content with an internal link",
     :org/body
     [{:line-type :unordered-list,
       :text
       "- Wide net for [[file:20200609220548-capture_should_be_easy.org][easy capture]]"}]}]})

(deftest markdown-with-link-test-conversion
  (testing "includes markdown-style links"
    (let [example-org example-item-with-link
          lines       (->> example-org
                           (#(sut/item->md-body % {:link-prefix "/notes"}))
                           (remove empty?))
          line        (->> lines
                           (filter #(string/starts-with? % "- Wide"))
                           first)]
      (is (= "- Wide net for [easy capture](/notes/20200609220548-capture_should_be_easy)"
             line))))
  (testing "no link prefix by default"
    (let [example-org example-item-with-link
          lines       (->> example-org
                           sut/item->md-body
                           (remove empty?))
          line        (->> lines
                           (filter #(string/starts-with? % "- Wide"))
                           first)]
      (is (= "- Wide net for [easy capture](/20200609220548-capture_should_be_easy)"
             line)))))

(deftest markdown-with-link-test-links-func
  (let [example-org example-item-with-link
        links       (->> example-org sut/item->links)]
    (testing "includes markdown-style links"
      (is (= {:name "easy capture"
              :link "20200609220548-capture_should_be_easy"}
             (first links))))))

(def example-item-with-line-broken-link
  {:org/level :level/root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :org/items
   [{:org/level 1,
     :org/name  "content with a link",
     :org/body
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
              "  capture](/20200609220548-capture_should_be_easy)"]
             (->> lines (drop 1)))))))

(def example-header-link
  {:org/level :level/root,
   :org/name  "yodo, the pitch and demo outline",
   :org/body
   [{:line-type :comment, :text "#+TITLE: yodo, the pitch and demo outline"}]
   :org/items
   [{:org/level 1,
     :org/name
     "[[file:link-name.org][text name]] blah"}]})

(deftest markdown-with-link-in-header-test
  (let [example-org example-header-link
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "includes markdown-style links"
      (is (contains? (set lines)
                     "# [text name](/link-name) blah")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; test rn by example - check that they show up in fixture-dir/example.md
(deftest markdown#backlinks-test
  (testing "backlinks are added as expected"
    (let [md-items
          (sut/org-dir->md-dir fixture-dir fixture-dir {:dry-run true})
          example-md (some->> md-items
                              (filter (comp #{"Example Org File"} :name))
                              first)
          body-lines (some-> example-md
                             :body
                             (->> (map string/trim)
                                  (into #{})))
          ]
      (is md-items)
      (is example-md)
      (println body-lines)
      (is (contains? body-lines "# Backlinks"))
      (is (contains? body-lines "- [Dated Example](/20200618104339-dated-example)"))
      (is (contains? body-lines "- [Linked Org File](/linked-org-file)")))))

(deftest markdown#backlinks-link-prefix
  (testing "backlinks honor link-prefixes as well"
    (let [md-items
          (sut/org-dir->md-dir fixture-dir fixture-dir {:dry-run     true
                                                        :link-prefix "/notes"})
          example-md (some->> md-items
                              (filter (comp #{"Example Org File"} :name))
                              first)
          body-lines (some-> example-md
                             :body
                             (->> (map string/trim)
                                  (into #{})))]
      (is md-items)
      (is example-md)
      (println body-lines)
      (is (contains? body-lines "# Backlinks"))
      (is (contains? body-lines "- [Dated Example](/notes/20200618104339-dated-example)"))
      (is (contains? body-lines "- [Linked Org File](/notes/linked-org-file)")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; external links
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def example-item-with-external-link
  {:org/level :root,
   :org/name  "Example Org File",
   :org/body  [{:line-type :comment, :text "#+TITLE: Example Org File"}]
   :org/items
   [{:org/level 1,
     :org/name  "content with an external link",
     :org/body
     [{:line-type :unordered-list,
       :text
       "- Repo for [[https://github.com/russmatney/org-crud][org-crud for clojure]]"}]}]})

(deftest markdown-with-external-link-conversion
  (let [example-org example-item-with-external-link
        lines       (->> example-org sut/item->md-body
                         (remove empty?))]
    (testing "includes markdown-style external links"
      (is (= "- Repo for [org-crud for clojure](https://github.com/russmatney/org-crud)"
             (->> lines
                  (filter #(string/starts-with? % "- Repo"))
                  first))))))
