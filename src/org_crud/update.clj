(ns org-crud.update
  (:require
   [clojure.string :as string]
   [clojure.set :as set]
   [me.raynes.fs :as fs]
   [org-crud.core :as org]
   [org-crud.util :as util]
   [tick.alpha.api :as t]))

(def multi-prop-keys #{:repo-ids})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> org lines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status->status-text [status]
  (when status
    (case status
      :status/cancelled   "CANCELLED"
      :status/done        "[X]"
      :status/not-started "[ ]"
      :status/in-progress "[-]")))

(defn append-tags [line tags]
  (if-not tags
    line
    (let [tags
          (if (coll? tags) (set tags) (set [tags]))]
      (str line
           (when (seq tags)
             (str " :"
                  (string/join ":" tags)
                  ":"))))))

(defn new-property-text [key value]
  (str (string/lower-case key) ": " value))

(defn prop->new-property [[k val]]
  (if (coll? val)
    (map-indexed (fn [i v]
                   (new-property-text (str k (when (> i 0) "+")) v)) val)
    (new-property-text k val)))

(defn new-property-bucket [props]
  (let [res
        (flatten
          (seq [":PROPERTIES:"
                (sort (flatten (map prop->new-property props)))
                ":END:"]))]
    res))

(defn body->lines [body]
  (reduce
    (fn [agg line]
      (cond
        ;; includes blank lines
        ;; also writes scheduled lines
        (:text line)
        (conj agg (:text line))

        (and (= :block (:type line))
             (= "SRC" (:block-type line)))
        (apply conj agg (flatten [(str "#+BEGIN_SRC " (:qualifier line))
                                  (map :text (:content line))
                                  "#+END_SRC"]))

        (and (= :drawer (:type line))
             (= :property-drawer-item (some-> line :content first :line-type)))
        ;; skip property drawers, they are handled elsewhere
        ;; could write these here, but i like them coming from `props` as a map
        agg

        :else
        (do
          (println "unhandled line in item->lines/body->lines" line)
          agg)))
    []
    body))

(defn item->lines
  ([item] (item->lines item (:level item)))
  ([{:keys [name props tags body status items]} level]
   (if (= :root level)
     (body->lines body)
     (let [level          (or level 1)
           level-str      (apply str (repeat level "*"))
           headline       (str level-str
                               (when status
                                 (str " " (status->status-text status)))
                               " " name)
           headline       (append-tags headline tags)
           prop-lines     (new-property-bucket props)
           body-lines     (body->lines body)
           children-lines (->> items
                               (mapcat item->lines))]
       (concat
         (conj
           (concat prop-lines body-lines)
           headline)
         children-lines)))))

(comment
  (item->lines {:name "hi" :tags ["next"] :props {:hi :bye}} 3))

(defn append-to-file!
  [path lines]
  (let [as-str (string/join "\n" lines)]
    (spit path (str "\n\n" as-str) :append true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tag Updates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-remove-tag?
  "Returns true if the passed val is a [:remove \"tag\"] val"
  [v]
  (when (and v (coll? v))
    (some-> v first (= :remove))))

(defn tag-to-remove
  "Returns true if the passed val is a [:remove \"tag\"] val"
  [v]
  (when (and v (coll? v))
    (some-> v second)))

(defn update-tags
  [current-tags tags-update]
  (let [to-remove (if (is-remove-tag? tags-update)
                    (set [(tag-to-remove tags-update)])
                    #{})

        tags (if (is-remove-tag? tags-update)
               #{}
               (if (coll? tags-update) (set tags-update) (set [tags-update])))

        tags (set/union current-tags tags)]
    (set/difference tags to-remove)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Prop Updates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-remove?
  "Returns true if the passed map-val is a remove signal."
  [[_k v]]
  (when (and v (coll? v))
    (some-> v first (= :remove))))

(defn updated-props [old-props props-update]
  (let [remove-props (->> props-update
                          (filter is-remove?)
                          (into {}))
        props-update (remove is-remove? props-update)
        merged-props (util/merge-maps-with-multi
                       multi-prop-keys old-props props-update)
        merged-props (map (fn [[k vs]]
                            (if-let [remove-signal (get remove-props k)]
                              [k (let [to-remove (second remove-signal)]
                                   (remove #(= to-remove %) vs))]
                              [k vs])) merged-props)
        merged-props (map
                       (fn [[k v]]
                         (if (coll? v)
                           [k (sort (set v))]
                           [k v]))
                       merged-props)
        merged-props (remove (comp nil? second) merged-props)]
    merged-props))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update fn helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-update
  "Updates the passed item based on the `up`date passed"
  [item up]
  (cond-> item
    (:status up)
    (assoc :status (:status up))

    (seq (:tags up))
    (update :tags update-tags (:tags up))

    (:add-item up)
    (update :items (fn [items]
                     (conj items (-> (:add-item up)
                                     (assoc :level
                                            (+ 1 (:level item)))))))

    (:name up)
    (assoc :name (:name up))

    (seq (:props up))
    (update :props updated-props (:props up))))

(defn matching-items? [it item]
  (or
    (and (:id it)
         (= (:id it) (:id item)))
    ;; try to match on name if no id
    (and
      (not (:id it))
      (not (:id item))
      (:name it)
      (= (:name it) (:name item)))))

(defn update-items
  "Traverses the `parsed` org structure, applying `update` to a child that
  matches item. Returns the updated org structure."
  [parsed item up]
  (let [is-delete? (= :delete-item up)]
    (->> parsed
         (reduce
           (fn [agg it]
             (if is-delete?
               (if (matching-items? it item)
                 ;; don't conj here to remove the item
                 (assoc agg :deleting? true)
                 (if (and (:deleting? agg)
                          (> (:level it)
                             (:level item)))
                   ;; don't conj here to remove nested items
                   agg
                   ;; remove deleting?, appending the rest
                   (-> agg
                       (update :items conj it)
                       (dissoc :deleting?))))

               (update agg :items
                       conj (if (matching-items? it item)
                              (apply-update it up)
                              it))))
           {:items []})
         :items
         (remove nil?))))

(defn write-updated
  "Writes the passed org-structure to disk as a .org file.
  Clears whatever was in the file before writing."
  [path items]
  (when path
    (let [lines  (reduce (fn [acc item]
                           (concat acc (item->lines item))) [] items)
          as-str (string/join "\n" lines)]
      (spit path as-str))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Update function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update!
  ([item up] (update! (util/item->org-path item) item up))
  ([path item up]
   (println "Updating props TWO"
            {:path      path         :item-name (:name item)
             :item-type (:type item) :update    up})
   (let [parsed-items (org/path->items path)
         updated      (update-items parsed-items item up)]
     (write-updated path updated))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public add function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append-top-level
  [path item]
  (let [lines (item->lines
                (update item :props
                        (fn [props]
                          (merge {:added-at (util/now)} props))) 1)]
    (append-to-file! path lines)))

(defn add-to-context
  [path item context]
  (println (dissoc context :org-section))
  (update! path context
           {:add-item
            (update item :props
                    (fn [props]
                      (merge {:added-at (util/now)} props)))}))

(defn add-to-file!
  "Adds an item as an org headline to the indicated filepath.
  `context` indicates where to put the item.
  If `context` is `:top-level`, the item is added as the last top level in the file.
  "
  [path item context]
  (cond
    (= :top-level context)
    (append-top-level path item)

    (map? context)
    (add-to-context path item context)

    :else
    (println "Unsupported add! context! " context)))

(defn add-item!
  "Adds a passed item to the passed context.
  Uses the context to find the org-path, falling back
  to the item if the context is :top-level."
  [item context]
  (let [org-path (if (map? context)
                   (util/item->org-path context)
                   (util/item->org-path item))]
    (if (fs/file? org-path)
      (add-to-file! org-path item context)
      (println "Item add attempted for bad org-path" {:org-path org-path
                                                      :item     item}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete-from-file!
  "Deletes the item passed, if a match is found in the path"
  [path item]
  (update! path item :delete-item))

(defn delete-item!
  "Deletes the item passed, if a match is found in the path"
  [item]
  (let [org-path (util/item->org-path item)]
    (if (fs/file? org-path)
      (delete-from-file! org-path item)
      (do
        (println item)
        (println "Item delete attempted for bad org-path" org-path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Refile items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn refile-within-file! [path item context]
  (println "Refiling item within file"
           {:file path :item item :context context})
  (delete-from-file! path item)
  (add-to-file! path item context))

(defn refile! [item context]
  (println "Refiling item to context" {:item item :context context})
  (delete-item! item)
  (add-item! item context))

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
  (let [metadata (util/get-all content {:line-type :metadata})
        metadata (map :text metadata)]
    metadata))

(defn ->drawer
  [{:keys [content]}]
  (let [drawer (util/get-one content {:type :drawer})]
    (map :text (:content drawer))))

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

(defn ->id [{:keys [name] :as hl}]
  (let [props   (->properties hl)
        prop-id (:id props)]
    (if prop-id
      prop-id
      (when name
        (second (re-find #"(\d\d) " name))))))

(defn ->name [{:keys [name type content]}]
  (cond
    (and name (= type :section))
    (second (re-find
              #"\*?\*?\*?\*? ?\[?[ X-]?\]?(?:TODO|DONE|CANCELLED)? ?(.*)" name))

    (= type :root)
    (let [text (some->> (util/get-one
                          content {:line-type :comment
                                   :text      #(string/includes?
                                                 (string/lower-case %)
                                                 "#+title")})
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
