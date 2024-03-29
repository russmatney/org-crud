(ns org-crud.markdown
  (:require
   [clojure.string :as string]
   [babashka.fs :as fs]
   [clojure.set :as set]
   [org-crud.core :as org]
   [org-crud.util :as util]))

;; TODO non-existent local links still get through
;; TODO handle custom local link formats (jekyll example is broken)
;; TODO more passed frontmatter key/vals (no styling in jekyll example)
;; TODO handle custom post date source (not just org-roam filename style)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> date-str
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->file-basename
  ([item] (item->file-basename item {}))
  ([item {:keys [drop-datetime]}]
   (let [full-basename
         (some-> item
                 :org/source-file
                 fs/file-name
                 fs/split-ext
                 first)]
     (if (and drop-datetime (re-seq #"^\d{14}-" full-basename))
       (->> full-basename (drop 15) (apply str))
       full-basename))))

(defn item->date-str
  ([item] (item->date-str item {}))
  ([item opts]
   (let [basename (-> item (item->file-basename opts))]
     (when (re-seq #"^\d{8}" basename)
       (some->> basename
                (take 8)
                (apply str)
                ((fn [s]
                   (string/replace
                     s #"(\d\d\d\d)(\d\d)(\d\d)"
                     "$1-$2-$3"))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> link, filename
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->link
  ([item] (item->link item {}))
  ([item opts]
   ;; TODO build jekyll-style or templated link
   (-> item (item->file-basename opts))))

(defn item->md-filename
  ([item] (item->md-filename item {}))
  ([item {:as opts :keys [blog-type]}]
   (cond
     (= "jekyll" blog-type)
     (let [date-str (item->date-str item opts)
           basename (item->file-basename item (assoc opts :drop-datetime true))]
       (str date-str "-" basename ".md"))

     ;; default
     (or true (= "gatsby" blog-type))
     (-> item (#(item->link % opts)) (str ".md"))
     )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->frontmatter
  ([item] (item->frontmatter item {}))
  ([item {:keys [tags] :as opts}]
   (let [name     (:org/name item)
         basename (item->file-basename item opts)
         name     (or name (str "Daily Note for " basename))
         tags     (set/union tags (or (:org/tags item) #{}))
         date-str (item->date-str item opts)]
     (cond-> ["---"
              (str "title: \"" name "\"")]

       (seq date-str)
       (concat [(str "date: " date-str)])

       (> (count tags) 0)
       (concat
         (flatten [(str "tags:")
                   (->> tags (map (fn [tag] (str "  - " tag))))]))

       true
       (concat ["---"])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> markdown body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn body-str->file-refs [s]
  (some->> s
           (re-seq #"\[\[file:([^\]]*)\]\[[^\]]*\]\]")
           (map second)))

(defn markdown-link
  [{:keys [name link]}]
  (str "[" name "](" link ")"))

(defn ->md-link
  "Converts the passed link-text and link-uri to a working md link (or text).
  opts can include: `:id->link-uri`, `:file->link-uri`, `:args->link-text`.

  org roam uses id: prefixed links, so the passed opt funcs will likely lookup and return
  a uri if the link should be exposed.

  org uses file: links, most of these could be ignored, but perhaps some could link to
  files on github (if they are links to repo paths, for eg.). Not sure how they'd be perma-linked
  though.

  http: and other links will pass through here just fine."
  [{:keys [link-text link-uri] :as link-args} opts]
  (let [link-type (cond
                    (string/starts-with? link-uri "id:")   :id
                    (string/starts-with? link-uri "file:") :file
                    :else                                  nil)]
    (cond
      (and (= link-type :id) (:id->link-uri opts))
      (let [id       (some-> link-uri (string/split #"id:") second)
            link-uri ((:id->link-uri opts) id)]
        (if link-uri (markdown-link {:name link-text :link link-uri}) link-text))

      (and (= link-type :file) (:file->link-uri opts))
      (let [file     (some-> link-uri (string/split #"file:") second)
            link-uri ((:file->link-uri opts) file)]
        (if link-uri (markdown-link {:name link-text :link link-uri}) link-text))

      ;; only called with :id or :file link types
      (and link-type (:args->link-text opts))
      (let [text ((:args->link-text opts) link-args)]
        (or text link-text))

      ;; this path is deprecated but left in for now
      (and (string/starts-with? link-uri "id:") (:fetch-item opts))
      (let [fetch-item (:fetch-item opts)
            id         (some-> link-uri (string/split #"id:") second)]
        (if-let [item (fetch-item id)]
          (let [link (some-> item :org/source-file fs/file-name
                             fs/split-ext first (#(str % ".html")))]
            (if link
              (markdown-link {:name link-text :link link})
              (do
                (println "could not create link, returning raw link")
                (markdown-link {:name link-text :link link-uri}))))
          (do
            (println "fetch-item returned nil, using raw link" link-uri)
            (markdown-link {:name link-text :link link-uri}))))

      ;; deprecated, was once a viable fallback
      (string/starts-with? link-uri "file:")
      (let [link-prefix (or (:link-prefix opts) "")
            link-uri
            (some-> link-uri
                    fs/file-name fs/split-ext first
                    (string/replace "file:" "")
                    (#(str link-prefix "/" %)))]
        (markdown-link {:name link-text :link link-uri}))

      ;; used for normal http: links
      :else (markdown-link {:name link-text :link link-uri}))))

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
         (let [link-text (some->> res (drop 2) first)
               link-uri  (some->> res (drop 1) first)]
           ;; TODO write unit test
           (cond
             (and link-text (:drop-links opts))
             link-text

             ;; drop org `id:uuid` links
             (and link-uri
                  (:drop-id-links opts)
                  (re-seq #"^id:.*" link-uri ))
             link-text

             (and link-text link-uri)
             (->md-link {:link-text link-text :link-uri link-uri} opts)

             :else link-text)))))))

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
          (#{"SRC" "src"} (:block-type line)))
     (flatten [(str "``` " (:qualifier line))
               (map #(body-line->md-lines % opts) (:content line))
               "```"]))))

(defn item->md-body
  "Handles :keep-item and :remove-item.
  If the parent is kept, all children will be kept."
  ([item] (item->md-body item {}))
  ([item opts]
   (let [remove-item (:remove-item opts)
         ;; consider a default that filters everything
         keep-item   (:keep-item opts (constantly true))

         keep-parent (:keep-parent opts (keep-item item))
         child-lines (mapcat #(item->md-body %
                                             (cond-> opts
                                               keep-parent
                                               (assoc :keep-parent keep-parent)))
                             (cond->> (:org/items item)

                               remove-item
                               (remove remove-item)

                               (not keep-parent)
                               (filter keep-item)))
         header-line
         (if (int? (:org/level item))
           (str (apply str (repeat (:org/level item) "#")) " "
                (-> item
                    :org/name
                    (org-line->md-line opts)))
           "")
         body-lines (->> item
                         :org/body
                         (remove #(= (:line-type %) :comment))
                         (mapcat #(body-line->md-lines % opts))
                         (remove nil?))
         body-lines (when (seq body-lines)
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
