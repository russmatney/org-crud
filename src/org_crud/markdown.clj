(ns org-crud.markdown
  (:require
   [clojure.string :as string]
   [org-crud.fs :as fs]
   [org-crud.core :as org]
   [org-crud.util :as util]
   [clojure.set :as set]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> link, filename
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->link
  ([item] (item->link item {}))
  ([item _opts]
   (-> item
       :org/source-file
       fs/base-name
       fs/split-ext
       first)))

(defn markdown-link
  [{:keys [name link]}]
  (str "[" name "](" link ")"))

(defn item->md-filename
  ([item] (item->md-filename item {}))
  ([item opts]
   (-> item (#(item->link % opts)) (str ".md"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->frontmatter
  ([item] (item->frontmatter item {}))
  ([item {:keys [tags]}]
   (let [name     (:org/name item)
         basename (some-> item
                          :org/source-file
                          fs/base-name
                          fs/split-ext
                          first)
         name     (or name (str "Daily Note for " basename))
         tags     (set/union tags (or (:org/tags item) #{}))
         date-str (if (re-seq #"^\d{8}" basename)
                    (some->> basename
                             (take 8)
                             (apply str)
                             ((fn [s]
                                (string/replace
                                  s #"(\d\d\d\d)(\d\d)(\d\d)"
                                  "$1-$2-$3"))))
                    basename)]
     (cond-> ["---"
              (str "title: \"" name "\"")
              (str "date: " date-str)]

       (> (count tags) 0)
       (concat
         (flatten [(str "tags:")
                   (->> tags (map (fn [tag] (str "  - " tag))))]))

       true
       (concat ["---"])))))

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
  ([s] (org-links->md-links s {}))
  ([s opts]
   (when s
     (string/replace
       s
       #"\[\[([^\]]*)\]\[([^\]]*)\]\]"
       (fn [res]
         (let [file-path (some->> res (drop 1) first
                                  ((fn [raw-link]
                                     (println raw-link)
                                     (cond
                                       (string/starts-with? raw-link "file:")
                                       (let [link-prefix (or (:link-prefix opts) "")]
                                         (some-> raw-link
                                                 fs/base-name fs/split-ext first
                                                 (string/replace "file:" "")
                                                 (#(str link-prefix "/" %))))

                                       :else raw-link))))
               link-text (some->> res (drop 2) first)]
           (when (and file-path link-text)
             (markdown-link {:name link-text :link file-path}))))))))

(comment
(org-links->md-links
  "[[https://github.com/russmatney/org-crud][link to external repo]] for accumulating a design or an approach")
(org-links->md-links
  "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach")
)

(defn org-line->md-line
  ([s] (org-line->md-line s {}))
  ([s opts]
   (-> s
       (string/replace #"~([^~]*)~" "`$1`")
       (org-links->md-links opts))))

(defn body-line->md-lines
  ([line] (body-line->md-lines line {}))
  ([line opts]
   (cond
     (contains? #{:blank :table-row :unordered-list} (:line-type line))
     [(-> (:text line) (org-line->md-line opts))]

     (and (= :block (:type line))
          (= "SRC" (:block-type line)))
     (flatten [(str "``` " (:qualifier line))
               (map #(body-line->md-lines % opts) (:content line))
               "```"]))))

(defn item->md-body
  ([item] (item->md-body item {}))
  ([item opts]
   (let [child-lines (mapcat #(item->md-body % opts) (:org/items item))
         header-line
         (if (int? (:org/level item))
           (str (apply str (repeat (:org/level item) "#")) " "
                (-> item
                    :org/name
                    (org-line->md-line opts)))
           "")
         body-lines  (->> item
                          :org/body
                          (remove #(= (:line-type %) :comment))
                          (mapcat #(body-line->md-lines % opts))
                          (remove nil?))
         body-lines  (when (seq body-lines)
                       (->> body-lines
                            (string/join "\n")
                            org-links->md-links
                            ((fn [s] (string/split s #"\n")))
                            ))]
     (concat [header-line] body-lines child-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->strs [item]
  (let [name      (:org/name item)
        body-strs (->> (:org/body item)
                       (map :text))]
    (concat [name] body-strs)))

(defn all-body-strs [item]
  (loop [items    [item]
         all-strs []]
    (let [children (->> items (mapcat :org/items))
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

(defn item->links
  ([item] (item->links item {}))
  ([item _opts]
   (->> item
        all-body-strs
        (string/join "\n")
        str->file-refs
        (map (fn [[link text]]
               {:name text
                :link (-> link
                          (string/replace #"\n" ""))})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Building backlinks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn process-backlinks
  ([md-items] (process-backlinks {} md-items))
  ([{:keys [link-prefix]} md-items]
   (let [link->md-items (->> md-items
                             (map (fn [md-it]
                                    (update md-it :links
                                            (fn [links]
                                              (map :link links)))))
                             (util/multi-group-by :links))]
     (->> md-items
          (map
            (fn [md-item]
              (assoc md-item :backlinks
                     (let [linked-md-items
                           (-> md-item :self-link link->md-items)]
                       (->> linked-md-items
                            (map
                              (fn [linked-md-item]
                                {:name (:name linked-md-item)
                                 :link
                                 ;; TODO safety/dedupe around extra "/"
                                 (str link-prefix "/"
                                      (:self-link linked-md-item))})))))))))))

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

(defn item->md-lines
  ([item] (item->md-lines item {}))
  ([item opts]
   (concat
     (item->frontmatter item opts)
     (item->md-body item opts))))

(defn item->md-item
  ([item] (item->md-item item {}))
  ([item opts]
   {:filename  (item->md-filename item opts)
    :body      (item->md-lines item opts)
    :name      (:org/name item)
    :self-link (item->link item opts)
    :links     (item->links item opts)}))

(defn write-md-item [target-dir md-item]
  (spit (str target-dir "/" (:filename md-item))
        (->> md-item :body (string/join "\n"))))

(defn exclude-item? [item]
  (contains? (-> item :org/tags set) "private"))

(defn org-dir->md-dir
  ([source-dir target-dir]
   (org-dir->md-dir source-dir target-dir {}))
  ([source-dir target-dir {:as opts :keys [dry-run]}]
   (cond
     (not (fs/exists? source-dir))
     (println "Error: dir does not exist" source-dir)

     (not (fs/exists? target-dir))
     (println "Error: dir does not exist" target-dir)

     :else
     (doall
       (->> (org/dir->nested-items source-dir)
            (remove exclude-item?)
            (map #(item->md-item % opts))
            (process-backlinks opts)
            (map append-backlink-body)
            (#(cond->> %
                (not dry-run)
                (map (partial write-md-item target-dir)))))))))
