(ns org-crud.node
  (:require
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing an org-node helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->body-as-strings
  "Parses content :table-row :line-types into a list of strs"
  [{:keys [content]}]
  (->> content
       (filter (comp #{:table-row :unordered-list :definition-list :ordered-list :paragraph}
                     :line-type))
       (map :text)))

(defn ->body-string [raw]
  (->> (->body-as-strings raw)
       (string/join "\n")))

(defn ->body
  "Produces a somewhat structured body,
  with support for source blocks.

  Filters drawers.
  "
  [{:keys [content]}]
  (->> content
       (remove #(= :drawer (:type %)))
       (flatten)))

(defn ->metadata
  [{:keys [content]}]
  (->> content
       (filter (comp #(= % :metadata) :line-type))
       (map :text)))

(defn ->drawer
  [{:keys [content]}]
  (->> content
       (filter (comp #(= % :drawer) :type))
       first
       :content
       (map (comp string/trim :text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; property drawers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->prop-key [text]
  (let [[k _val] (string/split text #" " 2)]
    (when k
      (-> k
          (string/replace ":" "")
          (string/replace "_" "-")
          (string/replace "+" "")
          (string/replace "#" "")
          string/lower-case
          keyword))))

(defn ->prop-value [text]
  (when-let [_k (->prop-key text)]
    (let [[_k val] (string/split text #" " 2)]
      (when val
        (string/trim val)))))

(defn prop-lines->properties
  ([prop-lines] (prop-lines->properties prop-lines "org.prop"))
  ([prop-lines prefix]
   (if (seq prop-lines)
     (->> prop-lines
          (group-by ->prop-key)
          (map (fn [[k vals]]
                 (let [vals (map ->prop-value vals)
                       vals (if (> (count vals) 1)
                              ;; sorting just for testing convenience
                              (sort vals)
                              (first vals))]
                   (when (seq (name k))
                     [(keyword (str prefix "/" (name k))) vals]))))
          (remove nil?)
          (into {}))
     {})))

(defn ->properties [x]
  (prop-lines->properties
    (concat
      ;; check the drawer/prop-bucket for all
      (->drawer x)
      ;; check for additional root level props
      (when (= :root (:type x))
        ;; TODO stop after first non-comment?
        (->> x
             :content
             (filter #(= (:line-type %) :comment))
             (map :text))))))

(comment
  (->properties
    {:type :section
     :content
     [{:line-type :metadata
       :text      "DEADLINE: <2019-04-01 Mon>"}
      {:type :drawer
       :content
       [{:line-type :property-drawer-item :text ":ARCHIVE_TIME: 2019-04-07 Sun 10:23"}
        {:line-type :property-drawer-item :text ":ARCHIVE_FILE: ~/Dropbox/todo/todo.org"}
        {:line-type :property-drawer-item :text ":ARCHIVE_OLPATH: 2019-04-01"}
        {:line-type :property-drawer-item :text ":ARCHIVE_CATEGORY: todo"}
        {:line-type :property-drawer-item :text ":ARCHIVE_TODO: [X]"}
        {:line-type :property-drawer-item :text ":repo_ids: my/repo"}
        {:line-type :property-drawer-item :text ":repo_ids+: my/other-repo"}]}]
     :name "[X] create cards"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headline parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->level [{:keys [level]}]
  (or level :level/root))

(defn ->level-int [{:keys [level]}]
  (or level 0))

(defn ->id [raw]
  (when-let [id (-> raw ->properties :org.prop/id)]
    (let [id (cond
               (string? id) id

               (seq? id)
               (do
                 (println "Org node with multiple ids!?!"
                          (-> raw ->properties :org.prop/title))
                 (println "Using id: " (first id))
                 (first id)))]
      (try
        (java.util.UUID/fromString id)
        (catch Exception _e
          (println "Exception parsing :org/id" id)
          id)))))

(comment
  (first (reverse (list 1 5 8))))

(def headline-regex
  #"\*?\*?\*?\*?\*? ?(?:TODO|DONE|CANCELLED|SKIP|STRT)? ?(?:\[#[ABC]{1}\])? ?(.*)")

(defn ->name [{:keys [name type]}]
  (cond
    (and name (= type :section))
    (->> name
         (re-find headline-regex)
         second
         ((fn [s]
            (-> s
                (string/replace #"\[#[ABC]\] " "")
                (string/replace #"\[[ X-]\] " "")))))))

(comment
  (->name
    {:type :section
     :name
     "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach"})
  (->name {:name "* CANCELLED Reduce baggage" :type :section})
  (->name {:name "* TODO Reduce baggage" :type :section})
  (->name {:name "* [X] Reduce baggage" :type :section})
  (->name {:name "* Reduce baggage" :type :section})
  (->name {:name "* Reduce baggage"})
  (->name {:name "* TODO [#A] Reduce baggage" :type :section})
  (->name {:name "* [ ] [#A] Reduce baggage" :type :section})
  (let [raw
        {:type    :root,
         :content [{:line-type :comment, :text "#+TITLE: finally, a wiki!" }
                   {:line-type :blank, :text "" }
                   {:line-type :table-row, :text
                    "We finally arrived at [file:path.org][name-for-path]"}
                   {:line-type :blank, :text ""}
                   {:line-type :table-row, :text "It's a wiki"}]}]
    (->name raw)))

(defn ->raw-headline [{:keys [name level]}]
  (when level
    (str (apply str (repeat level "*")) " " name)))

(defn interpose-pattern
  "Replace parts of a string `s` matching `pattern` with function `replace-fn`."
  [s pattern replace-fn]
  (->> (loop [s s out []]
         (let [next-replacement (replace-fn s)
               parts            (string/split s pattern 2)]
           (if (< (count parts) 2)
             (concat out (if next-replacement (conj parts next-replacement) parts))
             (let [[new-s rest] parts
                   out          (concat out [new-s next-replacement])]
               (recur rest out)))))
       (remove #{""})))

(def link-re #"\[\[[^\]]*\]\[([^\]]*)\]\]")
(defn ->link-text
  "Used with interpost-pattern, which does not need all the matches, just the next one."
  [s]
  (some->>
    (re-seq link-re s)
    first
    second))

(comment
  (->link-text "* Reduce [[id:some-id][baggage]]")
  (re-seq link-re "* Reduce [[id:some-id][baggage]]")

  (->link-text "test [[id:some-id][with inner]] text [[id:other-id][todo]]")
  (re-seq link-re "test [[id:some-id][with inner]] text [[id:other-id][todo]]"))

(defn ->plain-string [s]
  (apply str (interpose-pattern s link-re ->link-text)))

(defn ->name-string
  "Returns a string version of the headline (removing org-links)"
  [node]
  (->plain-string (->name node)))


(comment
  (->name-string {:name "* [X] [#A] Reduce baggage" :type :section})
  (->name-string {:name "* [X] Reduce baggage" :type :section})
  (->name-string {:name "* Reduce baggage" :type :section})
  (->name-string {:name "* Reduce [[id:some-id][baggage]]" :type :section})
  (->name-string
    {:name "test [[id:some-id][with inner]] text [[id:other-id][todo]]" :type :section}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->tags
  ([raw] (->tags raw nil))
  ([{:keys [type tags]} props]
   (cond
     (= :root type)
     (->>
       ;; we're merging these, will write back as filetags
       [(:org.prop/filetags props)
        (:org.prop/roam-tags props)
        ;; (:org.prop/roam-aliases props) ;; TODO needs it's own handling
        ]
       (remove nil?)
       (mapcat #(string/split % #" |:"))
       (remove empty?)
       (into #{}))

     :else (-> tags (set)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; todo status
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def status-regex
  #"^(TODO|DONE|CANCELLED|SKIP|STRT|\[[ X-]\])")

(comment
  (re-seq status-regex "misc headline TODO")
  (re-seq status-regex "[X] done task")
  (re-seq status-regex "DONE done task")
  (re-seq status-regex "[-] started task")
  (re-seq status-regex "STRT started task")
  (re-seq status-regex "[ ] open task"))

;; TODO maybe parse a raw status as well
(defn ->todo-status
  [{:keys [name]}]
  (when name
    (let [matches (re-seq status-regex name)]
      (when (seq matches)
        (let [match (-> matches first second)]
          {:status-raw match
           :status
           (cond
             (#{"[-]" "STRT"} match)
             :status/in-progress

             (#{"[ ]" "TODO"} match)
             :status/not-started

             (#{"[X]" "DONE"} match)
             :status/done

             (#{"CANCELLED"} match)
             :status/cancelled

             (#{"SKIP"} match)
             :status/skipped

             :else nil)})))))

(comment
  (->todo-status {:name "[X] parse/pull TODOs from repo files"})
  (->todo-status {:name "[-] parse/pull TODOs from repo files"})
  (->todo-status {:name "DONE parse/pull TODOs from repo files"})
  (->todo-status {:name "SKIP parse/pull TODOs from repo files"})
  (->todo-status {:name "CANCELLED parse/pull TODOs from repo files"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; priority
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->priority
  [{:keys [name]}]
  (when name
    (when-let [match (re-seq #"\[#([ABC])\]" name)]
      (some-> match first second))))

(comment
  (->priority {:name "* TODO [#A] parse/pull TODOs from repo files"})
  (->priority {:name "* [X] [#A] parse/pull TODOs from repo files"})
  (->priority {:name "* parse/pull TODOs from repo files"}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn safe-find [pat s]
  (let [res (re-find pat s)]
    (when (and res (< 0 (count res)))
      (second res))))

(def org-date-regex
  "Somewhat hard-coded to examples like:
  - [2022-04-30 Sat 17:42]
  - <2022-04-30 Sat>

  # TODO incorporate more date formats
  "
  #": [<|\[](.{14,20})[>|\]]")

(defn ->date-pattern [label s]
  (let [pattern (re-pattern (str label org-date-regex))]
    ;; TODO consider parsing a tick date
    (safe-find pattern s)))

(defn ->date-for-label [label s]
  (some->
    (->date-pattern label s)))

(defn ->deadline [s] (->date-for-label "DEADLINE" s))
(defn ->scheduled [s] (->date-for-label "SCHEDULED" s))
(defn ->closed [s] (->date-for-label "CLOSED" s))

(defn metadata->date-map [s]
  (->>
    {:org/scheduled (->scheduled s)
     :org/deadline  (->deadline s)
     :org/closed    (->closed s)}
    (filter second)
    (into {})))

(defn ->dates [x]
  (let [metadata (->metadata x)]
    (if (seq metadata)
      (metadata->date-map (first metadata))
      {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; url parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->urls
  ;; parses an org node for urls from the name and body
  [x]
  (let [strs (conj (->body-as-strings x) (->name x))]
    (->> strs
         (map util/->url)
         (remove nil?))))

(comment
  (util/->url "read http://github.com"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; links-to
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; parsing roam `[id:<uuid>]` links

(defn body-str->links-to
  "Parse roam links out of a node body."
  [s]
  (some->> s
           (re-seq #"\[\[id:([^\]]*)\]\[([^\]]*)\]\]")
           (map rest)
           (remove nil?)
           (map (fn [[id text]]
                  (let [matching-lines
                        (->> s
                             string/split-lines
                             (filter (fn [line]
                                       (string/includes? line id)))
                             (into []))]
                    {:link/id                  (java.util.UUID/fromString id)
                     :link/text                (-> text
                                                   string/trim
                                                   (string/split #"\s")
                                                   (->> (string/join " ")))
                     :link/context             s
                     :link/context-str         (->plain-string s)
                     :link/matching-lines      matching-lines
                     :link/matching-lines-strs (->> matching-lines
                                                    (map ->plain-string)
                                                    (into []))
                     })))
           (into #{})))

(defn ->links-to [x]
  (->>
    (concat
      (-> x ->body-string body-str->links-to)
      (-> x ->name body-str->links-to))
    (remove nil?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; images
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->date [path]
  (let [fname (fs/file-name path)
        [y m d h min]
        (some->
          (re-seq #"(\d{4})-(\d{2})-(\d{2})[ |_](\d{2})[-|:](\d{2})" fname)
          first
          rest)]
    ;; TODO consider parsing a tick date
    (when (and y m d)
      (str y "-" m "-" d
           (when (and h min)
             (str " " h ":" min))))))

(comment

  (re-seq #"(\d{4})-(\d{2})-(\d{2}) (\d{2})-(\d{2})"
          "[[~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4]]")
  (path->date "[[~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4]]")
  (path->date
    "[[~/Screenshots/screenshot_2022-12-04_14:52:04-0500.jpg]]")

  (path->date
    "[[~/Screenshots/screenshot_with-some-other-fname.jpg]]")
  )

(defn is-image-line?
  "Could get much stricter, and consume the rest of the filtering.
  Maybe whitelist some expected extensions?"
  [text]
  (and
    (not (re-seq #"id:" text))
    (re-seq #"\/" text)
    (re-seq #"^\[\[" text)
    (re-seq #"]]$" text)))

(defn ->images [x]
  (let [content (:content x)
        image-paths
        (->> content (filter (comp #{:table-row} :line-type))
             (map :text)
             (filter is-image-line?)
             (map (fn [l]
                    (-> l
                        (string/replace "[[" "")
                        (string/replace "]]" ""))))
             (remove (fn [s] ;; remove named links that snuck through
                       (string/includes? s "][")))
             (filter fs/extension))
        paths-and-indexes
        (->> image-paths
             (map (fn [p]
                    (->> content (map-indexed vector)
                         (map
                           (fn [[i c]]
                             (when ;; not every c has :text (e.g. blocks/drawers)
                                 (some-> c :text (string/includes? p))
                               [p i])))
                         (remove nil?)
                         first)))
             (remove nil?))
        paths-and-comments
        (->> paths-and-indexes
             (map (fn [[path index]]
                    (let [rng (range (dec index) -1 -1)
                          comments
                          (->> rng
                               ;; walk backwards to collect properties
                               (take-while (fn [i]
                                             (let [line (get content i)]
                                               (when (and
                                                       line
                                                       (-> line :line-type #{:comment}))
                                                 line))))
                               (map #(get content %)))]
                      [path comments]))))]
    (->> paths-and-comments
         (map (fn [[path comments]]
                (let [parsed-date (path->date path)]
                  (merge
                    (prop-lines->properties (map :text comments) "image")
                    {:image/path          path
                     :image/path-expanded (str (fs/expand-home path))
                     :image/extension     (fs/extension path)}
                    (when parsed-date
                      {:image/date-string parsed-date}))))))))


(comment
  (->images
    {:type    :section
     :content [{:line-type :comment
                :text      "#+name: gameplay recording from HatBot"}
               {:line-type :comment
                :text      "#+caption: Some clip or other"}
               {:line-type :table-row
                :text      "[[~/Dropbox/gifs/Peek 2023-03-13 09-30.mp4]]"}]
     :level   1
     :name    "blog supporting images"
     :tags    ["blog" "clip"]
     :kw      nil}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; word count
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn str->count [str]
  (when str
    (some->
      (string/split str #" ")
      count)))

(defn ->word-count [item raw]
  (+ (or (some-> item :org/name str->count) 0)
     (or (some->> raw
                  ->body-as-strings
                  (map str->count)
                  (reduce + 0)) 0)))

(comment
  (->word-count nil nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; short path
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->short-path [source-file]
  (when (and source-file (fs/exists? source-file))
    (str (-> source-file fs/parent fs/file-name) "/" (fs/file-name source-file))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item - parsed org-items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->item [raw source-file]
  (let [last-modified (some-> source-file fs/last-modified-time str)]
    (-> (cond
          (#{:section} (:type raw))
          (let [{:keys [status status-raw]} (->todo-status raw)]
            (merge {:file/last-modified last-modified
                    :org/body           (->body raw)
                    :org/body-string    (->body-string raw)
                    :org/headline       (->raw-headline raw)
                    :org/id             (->id raw)
                    :org/images         (->images raw)
                    :org/level          (->level raw)
                    :org/level-int      (->level-int raw)
                    :org/links-to       (->links-to raw)
                    :org/name           (->name raw)
                    :org/name-string    (->name-string raw)
                    :org/priority       (->priority raw)
                    :org/short-path     (some-> source-file ->short-path)
                    :org/source-file    (some-> source-file fs/absolutize str)
                    :org/status         status
                    :org/status-raw     status-raw
                    :org/tags           (->tags raw)}
                   (->properties raw)
                   (->dates raw)))

          (#{:root} (:type raw))
          (let [props (->properties raw)]
            (merge {:file/last-modified last-modified
                    :org/body           (->body raw)
                    :org/body-string    (->body-string raw)
                    :org/id             (if-let [id (->id raw)]
                                          id (:org.prop/id props))
                    :org/images         (->images raw)
                    :org/level          (->level raw)
                    :org/level-int      (->level-int raw)
                    :org/links-to       (->links-to raw)
                    :org/name           (:org.prop/title props)
                    :org/name-string    (:org.prop/title props)
                    :org/short-path     (some-> source-file ->short-path)
                    :org/source-file    (some-> source-file fs/absolutize str)
                    :org/tags           (->tags raw props)
                    :org.prop/title     (some-> source-file fs/file-name)}
                   props
                   (->dates raw))))

        ((fn [item]
           (when item
             (-> item
                 (assoc :org/word-count (->word-count item raw))
                 (assoc :org/urls (-> raw ->urls set))
                 (->>
                   ;; remove nil fields
                   (remove (comp nil? second))
                   (into {})))))))))

(comment
  (require 'org-crud.parse)
  (def path (str (fs/home) "/todo/garden/bb_cli.org"))
  (def parsed (-> path org-crud.parse/parse-file first))
  (->item parsed path))
