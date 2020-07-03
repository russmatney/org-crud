(ns org-crud.headline
  (:require [org-crud.util :as util]
            [clojure.string :as string]
            [tick.alpha.api :as t]))

(def multi-prop-keys #{:repo-ids})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headline helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->body-as-strings
  "Parses content :table-row :line-types into a list of strs"
  [{:keys [content]}]
  (->> content
       (filter #(= (:line-type %) :table-row))
       (map :text)))

(defn ->body
  "Produces a somewhat structured body,
  with support for source blocks.

  Filters drawers.
  "
  [{:keys [content]}]
  (->> content
       (remove #(= :drawer (:type %)))
       (partition-by #(= :blank (:line-type %)))
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
       (map :text)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'journal' metadata
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Made to support one-off syntax in journal entries
;; May end up being useful elsewhere (commit bodies)

(def str-key->key
  {"Host" :host
   "L"    :listening
   "M"    :message})

(def start-keys
  (set
    (map
      (fn [[k _]]
        (str k ": ")
        )
      str-key->key)))

(defn ->meta-key-val
  "Splits the passed string on the first colon `:`,
  and selects a key based on that prefix.

  Uses `str-key->key` for setting the key.

  Otherwise uses the key from the string itself.
  "
  [{:keys [text]}]
  (when text
    (let [split (string/split text #":" 2)]
      (when (> (count split) 1)
        (let [[key-s val-s] split
              key           (str-key->key key-s)
              val-s         (string/trim val-s)
              val-s         (if (empty? val-s) nil val-s)]
          (if key
            [key val-s]
            [(keyword key-s) val-s]))))))


(defn ->only-journal-meta
  [body]
  (->> body
       (partition-by #(= :blank (:line-type %)))
       (first)
       (flatten)))

(defn is-blank? [{:keys [line-type]}] (= :blank line-type))

(defn ->journal-body
  ""
  [body]
  (let [groups-by-blanks
        (->> body (partition-by is-blank?))

        first-group (-> groups-by-blanks first)
        rst         (-> groups-by-blanks rest flatten)

        ;; filter first group to remove key-vals
        first-group (filter
                      (comp #(contains? start-keys %) :text)
                      first-group)

        ;; filter first of rest if it is a blankline AND first-group is empty
        rst (if (and (is-blank? (first rst))
                     (empty? first-group))
              (rest rst)
              rst)
        ]
    (flatten (conj rst first-group))))

(defn ->journal-meta
  "A map built up from key/val pairs on entries."
  [body]
  (let [j-meta   (->only-journal-meta body)
        meta-map (into {} (map ->meta-key-val j-meta))]
    meta-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; property drawers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def prop-parser
  "Contains some types with known parses.
  For now supports a few string->dates parses."
  ;; TODO how to support for all fields? sniff types?
  {:archive-time util/date->ny-zdt
   :added-at     util/date->ny-zdt
   :seen-at      util/date->ny-zdt
   :started-at   util/date->ny-zdt
   :finished-at  util/date->ny-zdt})

(comment
  (t/parse "2020-05-24T17:36-04:00[America/New_York]")
  (util/date->ny-zdt "2020-05-24T17:36-04:00[America/New_York]")
  (util/date->ny-zdt "2020-05-23T13:05:32.637-04:00[America/New_York]")
  (util/date->ny-zdt "2020-05-23T13:05:32.637-04:00[America/New_York]"))

(defn ->prop-key [text]
  (let [[key _val] (string/split text #" " 2)]
    (when key
      (-> key
          (string/replace ":" "")
          (string/replace "_" "-")
          (string/replace "+" "")
          string/lower-case
          keyword))))

(defn ->prop-value [text]
  (when-let [key (->prop-key text)]
    (let [[_k val] (string/split text #" " 2)]
      (when val
        (let [val (string/trim val)]
          (if-let [parser (prop-parser key)] (parser val) val))))))

(defn ->prop-key-val-map [text]
  {(->prop-key text) (->prop-value text)})

(defn lines->prop-map
  "Converts the passed props text-lines to a map,
  to aid merging with another map of props."
  ;; TODO need to combine matching keys
  [lines]
  (some->> lines
           (map ->prop-key-val-map)
           (apply (partial util/merge-maps-with-multi multi-prop-keys))))

(comment
  (->>
    (map ->prop-key-val-map [":hello: world" ":hello: sonny"])
    (apply (partial util/merge-maps-with-multi multi-prop-keys))))

(defn ->properties [x]
  (let [drawer-items (->drawer x)]
    (if (seq drawer-items)
      (->> drawer-items
           (group-by ->prop-key)
           (map (fn [[k vals]]
                  (let [vals (map ->prop-value vals)
                        vals (if (contains? multi-prop-keys k)
                               ;; sorting just for testing convenience
                               (sort vals)
                               (first vals))]
                    [k vals])))
           (into {}))
      {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; headline parsers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->level [{:keys [level]}] (or level :root))

(defn ->id [hl]
  (if-let [prop-id (-> hl ->properties :id)]
    prop-id
    (when (:name hl)
      (second (re-find #"(\d\d) " (:name hl))))))


(defn ->name [{:keys [name type content]}]
  (cond
    (and name (= type :section))
    (second (re-find
              #"\*?\*?\*?\*? ?\[?[ X-]?\]?(?:TODO|DONE|CANCELLED)? ?(.*)" name))

    (= type :root)
    (let [text (some->> content
                        (filter (fn [c]
                                  (and (= (:line-type c) :comment)
                                       (string/includes?
                                         (-> c :text string/lower-case)
                                         "#+title"))))
                        first
                        :text)]
      (when text
        (some->
          (re-find #".*TITLE: (.*)" text)
          second)))))

(comment
  (->name {:name "* CANCELLED Reduce baggage" :type :section})
  (->name {:name "* TODO Reduce baggage" :type :section})
  (->name {:name "* [X] Reduce baggage" :type :section})
  (->name {:name "* Reduce baggage"})
  (let [raw
        {:type    :root,
         :content [{:line-type :comment, :text "#+TITLE: finally, a wiki!" }
                   {:line-type :blank, :text "" }
                   {:line-type :table-row, :text
                    "We finally arrived at [file:path.org][name-for-path]"}
                   {:line-type :blank, :text ""}
                   {:line-type :table-row, :text "It's a wiki"}]}]
    (->name raw))
  )

(defn ->raw-headline [{:keys [name level]}]
  (when level
    (str (apply str (repeat level "*")) " " name)))

(defn ->tags [x]
  (-> x :tags (set)))

(defn has-number?
  [{:keys [name]}]
  (boolean (when name (second (re-find #"(\d\d) " name)))))

(defn has-TODO?
  [name]
  (let [name (if (string? name) name (:name name))]
    (boolean (when name
               (re-seq #"(\[( |X|-)\]|TODO|DONE)" name)))))

(defn is-headline? [s]
  (re-seq #"\*\*?\*?\*?" s))

(defn tags-in-progress? [x]
  (let [tags (->tags x)]
    (contains? tags "current")))

(comment
  (contains? #{"current"} "current"))

(defn ->todo-status
  "Returns either :not-started, :in-progress, or :done"
  [{:keys [name] :as x}]
  (when name
    (cond
      (or (re-seq #"(\[-\])" name) (tags-in-progress? x))
      :status/in-progress

      (re-seq #"(\[ \]|TODO)" name)
      :status/not-started

      (re-seq #"(\[X\]|DONE)" name)
      :status/done

      (re-seq #"CANCELLED" name)
      :status/cancelled

      :else nil)))

(comment
  (->todo-status
    {:name "[X] parse/pull TODOs from repo files"}))

(defn ->notes
  [{:keys [content]}]
  (let [text (map :text content)]
    {:raw  content
     :text text}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn safe-find [pat s]
  (let [res (re-find pat s)]
    (when (and res (< 0 (count res)))
      (second res))))

(defn ->date-pattern [label s]
  (let [pattern (re-pattern (str label ": <(.{14})>"))]
    (safe-find pattern s)))

(comment
  (->date-pattern "SCHEDULED" "SCHEDULED: <2020-03-18 Wed>"))

(defn ->date-for-label [label s]
  (some->
    (->date-pattern label s)
    (string/split #" ")
    (first)
    (t/date)
    (util/date->ny-zdt)))

(defn ->deadline [s] (->date-for-label "DEADLINE" s))

(defn ->scheduled [s] (->date-for-label "SCHEDULED" s))

(defn metadata->date-map [s]
  {:scheduled (->scheduled s)
   :deadline  (->deadline s)})

(defn ->dates [x]
  (let [metadata (->metadata x)]
    (if (seq metadata)
      (metadata->date-map (first metadata))
      {})))

(defn ->date-from-name [{:keys [name]}]
  (util/date->ny-zdt name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; url parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->urls
  ;; parses an org header for urls from the name and body
  [x]
  (let [strs (conj (->body-as-strings x) (->name x))]
    (->> strs
         (map util/->url)
         (remove nil?))))

(comment
  (util/->url "read http://github.com"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item - a general headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->item [raw] ;; expects not a string, but an parsed org map
  (cond
    (= :section (:type raw))
    (let [dates (->dates raw)]
      (merge
        dates
        {:org-section  raw
         :level        (->level raw)
         :id           (->id raw)
         :name         (->name raw)
         :raw-headline (->raw-headline raw)
         :tags         (->tags raw)
         :body         (->body raw)
         :status       (->todo-status raw)
         :props        (->properties raw)}))

    (= :root (:type raw))
    (let [dates (->dates raw)]
      (merge
        dates
        {:org-section raw
         :level       (->level raw)
         ;; :id          (->id raw)
         :name        (->name raw)
         ;; :tags        (->tags raw)
         :body        (->body raw)
         ;; :props       (->properties raw)
         }))))
