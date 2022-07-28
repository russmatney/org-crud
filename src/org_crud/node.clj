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
       (filter #(= (:line-type %) :table-row))
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

(defn ->properties [x]
  (let [prop-lines
        (concat
          ;; check the drawer/prop-bucket for all
          (->drawer x)
          ;; check for additional root level props
          (when (= :root (:type x))
            ;; TODO stop after first non-comment?
            (->> x
                 :content
                 (filter #(= (:line-type %) :comment))
                 (map :text))))]
    (if (seq prop-lines)
      (->> prop-lines
           (group-by ->prop-key)
           (map (fn [[k vals]]
                  (let [vals (map ->prop-value vals)
                        vals (if (> (count vals) 1)
                               ;; sorting just for testing convenience
                               (sort vals)
                               (first vals))]
                    [(keyword (str "org.prop/" (name k))) vals])))
           (into {}))
      {})))

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

(comment
  (keyword (str "level/" 1))
  (keyword (str "level/" :root)))

(defn ->id [hl]
  (when-let [id (-> hl ->properties :org.prop/id)]
    (try
      (java.util.UUID/fromString id)
      (catch Exception e
        (println "Exception parsing :org/id" id)
        id))))

(defn ->name [{:keys [name type]}]
  (cond
    (and name (= type :section))
    (->> name
         (re-find
           ;; TODO pull these out - depends on the config
           #"\*?\*?\*?\*? ?(?:TODO|DONE|CANCELLED|SKIP)? ?(?:\[#[ABC]{1}\])? ?(.*)")
         second
         ((fn [s] (string/replace s #"\[[ X-]\] " ""))))))

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

(defn ->todo-status
  [{:keys [name]}]
  (when name
    (cond
      (re-seq #"(\[-\])" name)
      :status/in-progress

      (re-seq #"(\[ \]|TODO)" name)
      :status/not-started

      (re-seq #"(\[X\]|DONE)" name)
      :status/done

      (re-seq #"CANCELLED" name)
      :status/cancelled

      (re-seq #"SKIP" name)
      :status/skipped

      :else nil)))

(comment
  (->todo-status
    {:name "[X] parse/pull TODOs from repo files"}))

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
                  {:link/id   (java.util.UUID/fromString id)
                   :link/text (-> text
                                  string/trim
                                  (string/split #"\s")
                                  (->> (string/join " ")))}))
           (into #{})))

(defn ->links-to [x]
  (-> x ->body-string body-str->links-to))


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
;; item - parsed org-items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->item [raw source-file]
  (-> (cond
        (#{:section} (:type raw))
        (merge {:org/level       (->level raw)
                :org/source-file (-> source-file fs/absolutize str)
                :org/id          (->id raw)
                :org/name        (->name raw)
                :org/headline    (->raw-headline raw)
                :org/tags        (->tags raw)
                :org/priority    (->priority raw)
                :org/body        (->body raw)
                :org/body-string (->body-string raw)
                :org/links-to    (->links-to raw)
                :org/status      (->todo-status raw)}
               (->properties raw)
               (->dates raw))

        (#{:root} (:type raw))
        (let [props (->properties raw)]
          (merge {:org/level       (->level raw)
                  :org/source-file (-> source-file fs/absolutize str)
                  :org/name        (:org.prop/title props)
                  :org/tags        (->tags raw props)
                  :org/body        (->body raw)
                  :org/body-string (->body-string raw)
                  :org/links-to    (->links-to raw)
                  :org/id          (if-let [id (->id raw)]
                                     id (:org.prop/id props))}
                 props
                 (->dates raw))))

      ((fn [item]
         (when item
           (-> item
               (assoc :org/word-count (->word-count item raw))
               (assoc :org/urls (-> raw ->urls set))))))))
