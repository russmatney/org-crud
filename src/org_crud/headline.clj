(ns org-crud.headline
  (:require
   [org-crud.util :as util]
   [clojure.string :as string]))


(def ^:dynamic *multi-prop-keys* #{})
(def ^:dynamic *prop-parser*
  "Contains some types with known parses.
  For now supports a few string->dates parses." {})

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
;; property drawers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
          (if-let [parser (*prop-parser* key)] (parser val) val))))))

(defn ->properties [x]
  (let [drawer-items (->drawer x)]
    (if (seq drawer-items)
      (->> drawer-items
           (group-by ->prop-key)
           (map (fn [[k vals]]
                  (let [vals (map ->prop-value vals)
                        vals (if (contains? *multi-prop-keys* k)
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
    (->> name
         (re-find
           #"\*?\*?\*?\*? ?(?:TODO|DONE|CANCELLED)? ?(.*)")
         second
         ((fn [s] (string/replace s #"\[[ X-]\] " ""))))

    (= type :root)
    (some->> content
             (filter
               (fn [c]
                 (and (= (:line-type c) :comment)
                      (string/includes?
                        (-> c :text string/lower-case)
                        "#+title"))))
             first
             :text
             (re-find #".*TITLE: (.*)")
             second)))

(comment

  (->name
    {:type :section
     :name
     "[[file:20200627150518-spaced_repetition_in_decision_making.org][Spaced-repetition]] for accumulating a design or an approach"})
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
    (->name raw)))

(defn ->raw-headline [{:keys [name level]}]
  (when level
    (str (apply str (repeat level "*")) " " name)))

(defn ->tags [{:keys [type content tags]}]
  (cond
    (= :root type)
    (let [text
          (some->>
            content
            (filter (fn [c]
                      (and (= (:line-type c) :comment)
                           (string/includes?
                             (-> c :text string/lower-case)
                             "#+roam_tags"))))
            first
            :text)]
      (when text
        (some->
          (->> text
               string/lower-case
               (re-find #".*roam_tags: (.*)"))
          second
          (string/split #" ")
          (into #{}))))

    :else (-> tags (set))))

(defn ->todo-status
  "Returns either :not-started, :in-progress, or :done"
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

      :else nil)))

(comment
  (->todo-status
    {:name "[X] parse/pull TODOs from repo files"}))

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

;; (defn ->date-for-label [label s]
;;   (some->
;;     (->date-pattern label s)
;;     (string/split #" ")
;;     (first)
;;     (t/date)
;;     (util/date->ny-zdt)))

;; (defn ->deadline [s] (->date-for-label "DEADLINE" s))

;; (defn ->scheduled [s] (->date-for-label "SCHEDULED" s))

;; (defn metadata->date-map [s]
;;   {:scheduled (->scheduled s)
;;    :deadline  (->deadline s)})

(defn ->dates [_x]
  {}
  ;; (let [metadata (->metadata x)]
  ;;   (if (seq metadata)
  ;;     (metadata->date-map (first metadata))
  ;;     {}))
  )

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

(defn ->item [raw]
  (cond
    (= :section (:type raw))
    (let [dates (->dates raw)]
      (merge
        dates
        {:level        (->level raw)
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
        {
         :level (->level raw)
         ;; :id          (->id raw)
         :name  (->name raw)
         :tags  (->tags raw)
         :body  (->body raw)
         ;; :props       (->properties raw)
         }))))
