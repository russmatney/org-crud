(ns org-crud.markdown
  (:require [clojure.string :as string]
            [org-crud.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->frontmatter [item]
  (let [name (:name item)]
    ["---" (str "title: " name) "---"]))

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
            (str "[" link-text "](/" file-path ")")))))))

(defn body-line->md-lines [line]
  (cond
    (contains? #{:blank :table-row :unordered-list} (:line-type line))
    [(:text line)]

    (and (= :block (:type line))
         (= "SRC" (:block-type line)))
    (flatten [(str "``` " (:qualifier line))
              (map body-line->md-lines (:content line))
              "```"])))

(defn item->md-body [item]
  (let [child-lines (mapcat item->md-body (:items item))
        header-line
        (if (int? (:level item))
          (str (apply str (repeat (:level item) "#")) " " (:name item))
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
                       ;; TODO support more line-types, etc
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
    "Written the same day as [[file:2020-06-10.org][this today file]].
Two [[file:2020-06-10.org][in]] [[file:2020-06-11.org][one]]."))

(defn item->links [item]
  (->> item
       all-body-strs
       (mapcat str->file-refs)
       (remove nil?)
       (map (fn [[link text]]
              {:text text
               :link (-> link
                         (string/replace #"\n" ""))}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> markdown
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->md-lines [item]
  (concat
    (item->frontmatter item)
    (item->md-body item)))

(defn item->md-filename [item]
  (-> item
      :source-file
      fs/base-name
      fs/split-ext
      first
      (str ".md")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public: converting to and writing a markdown file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->md-item [item]
  {:filename (item->md-filename item)
   :lines    (item->md-lines item)
   :links    (item->links item)})

(defn write-md-item [target-dir md-item]
  (spit (str target-dir "/" (:filename md-item))
        (->> md-item :lines (string/join "\n"))))
