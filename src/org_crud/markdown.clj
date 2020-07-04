(ns org-crud.markdown
  (:require [clojure.string :as string]
            [org-crud.fs :as fs]
            [org-crud.core :as org]
            [org-crud.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> link, filename
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->link [item]
  (-> item
      :source-file
      fs/base-name
      fs/split-ext
      first))

(defn markdown-link
  "TODO refactor /garden out of here, or into something configurable."
  [{:keys [name link]}]
  (str "[" name "](/garden/" link ")"))

(defn item->md-filename [item]
  (-> item item->link (str ".md")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->frontmatter [item]
  (let [name (:name item)
        name (or name (item->link item))
        tags (conj (or (:tags item) #{}) "garden")]
    (flatten ["---"
              (str "title: " name)
              (str "date: " "2020-07-04")
              (str "tags:")
              (->> tags (map (fn [tag] (str "  - " tag))))
              "---"])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> markdown body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn body-str->file-refs [s]
  (some->> s
           (re-seq #"\[\[file:([^\]]*)\]\[[^\]]*\]\]")
           (map second)))

(defn org-links->md-links
  "Rearranges org-links found in the string with the md style.
  The structure is a supported relative link usable with the gatsby-catch-links
  plugin.

  Works across line breaks within the string."
  [s]
  (when s
    (string/replace
      s
      #"\[\[file:([^\]]*)\]\[([^\]]*)\]\]"
      (fn [res]
        (let [file-path (some->> res (drop 1) first fs/base-name fs/split-ext first)
              link-text (some->> res (drop 2) first)]
          (when (and file-path link-text)
            (markdown-link {:name link-text :link file-path})))))))

(defn body-line->md-lines [line]
  (cond
    (contains? #{:blank :table-row :unordered-list} (:line-type line))
    [(:text line)]

    (and (= :block (:type line))
         (= "SRC" (:block-type line)))
    (flatten [(str "``` " (:qualifier line))
              (map body-line->md-lines (:content line))
              "```"])))

(comment
  (org-links->md-links
    "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach"))

(defn item->md-body [item]
  (let [child-lines (mapcat item->md-body (:items item))
        header-line
        (if (int? (:level item))
          (str (apply str (repeat (:level item) "#")) " "
               (org-links->md-links (:name item)))
          "")
        body-lines  (->> item
                         :body
                         (remove #(= (:line-type %) :comment))
                         (mapcat body-line->md-lines)
                         (remove nil?))
        body-lines  (when (seq body-lines)
                      (->> body-lines
                           (string/join "\n")
                           org-links->md-links
                           ((fn [s] (string/split s #"\n")))
                           ))]
    (concat [header-line] body-lines child-lines)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->strs [item]
  (let [name      (:name item)
        body-strs (->> (:body item)
                       (map :text))]
    (concat [name] body-strs)))

(defn all-body-strs [item]
  (loop [items    [item]
         all-strs []]
    (let [children (->> items (mapcat :items))
          strs     (->> items (mapcat item->strs))
          all-strs (concat all-strs strs)]
      (if (seq children)
        (recur children all-strs)
        all-strs))))

(defn str->file-refs [s]
  (some->> s
           (re-seq #"\[\[file:([^\]]*).org\]\[([^\]]*)\]\]")
           (map #(drop 1 %))))

(comment
  (str->file-refs "no links")

  (str->file-refs
    "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach")

  (str->file-refs
    "Written the same day as [[file:2020-06-10.org][this today file]].
Two [[file:2020-06-10.org][in]] [[file:2020-06-11.org][one]]."))

(defn item->links [item]
  (->> item
       all-body-strs
       (string/join "\n")
       str->file-refs
       (map (fn [[link text]]
              {:name text
               :link (-> link
                         (string/replace #"\n" ""))}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Building backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn process-backlinks [md-items]
  (let [link->md-items (->> md-items
                            (map (fn [md-it]
                                   (update md-it :links
                                           (fn [links]
                                             (map :link links)))))
                            (util/multi-group-by :links))]
    (->> md-items
         (map (fn [md-item]
                (assoc md-item :backlinks
                       (let [linked-md-items
                             (-> md-item :self-link link->md-items)]
                         (->> linked-md-items
                              (map
                                (fn [linked-md-item]
                                  {:name (:name linked-md-item)
                                   :link (:self-link linked-md-item)}))))))))))

(defn backlink->line [link]
  (str "- " (markdown-link link)))

(defn append-backlink-body [md-item]
  (if-let [links (seq (:backlinks md-item))]
    (update md-item :body (fn [body]
                            (concat body ["" "# Backlinks" ""]
                                    (map backlink->line links))))
    md-item))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public: converting to and writing a markdown file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->md-lines [item]
  (concat
    (item->frontmatter item)
    (item->md-body item)))

(defn item->md-item [item]
  {:filename  (item->md-filename item)
   :body      (item->md-lines item)
   :name      (:name item)
   :self-link (item->link item)
   :links     (item->links item)})

(defn write-md-item [target-dir md-item]
  (spit (str target-dir "/" (:filename md-item))
        (->> md-item :body (string/join "\n"))))

(defn org-dir->md-dir [source-dir target-dir]
  (->> (org/dir->nested-items source-dir)
       (map item->md-item)
       process-backlinks
       (map append-backlink-body)
       (map (partial write-md-item target-dir))))
