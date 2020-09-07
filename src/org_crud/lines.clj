(ns org-crud.lines
  (:require [clojure.string :as string]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; property bucket
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; root comment/properties
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-root-property-text [key value]
  (str "#+" (string/lower-case (name key)) ": " value))

(defn prop->new-root-property
  "Flattens multi-values."
  [blah]
  (let [[k val] blah]
    (if (coll? val)
      (map-indexed
        (fn [i v]
          (new-root-property-text
            (str k (when (> i 0) "+")) v)) val)
      (new-root-property-text k val))))

(defn new-root-property-bucket
  "Make sure #+title lands on top to support `deft`."
  [item]
  (let [item (update item :props #(into {} %))
        prop-bucket
        (->>
          (concat
            [[:title (:name item)]
             [:id (or (:id item) (-> item :props :id))]
             (when (->> item :tags (map string/trim) (remove empty?) seq)
               [:roam_tags (string/join " " (:tags item))])]
            (some-> item :props
                    (dissoc :title :id :tags :roam_tags :roam-tags)))
          (remove nil?)
          (remove (comp nil? second))
          (map prop->new-root-property)
          flatten
          (remove nil?))]
    prop-bucket))

(comment
  (new-root-property-bucket
    {:level :root
     :name  "hi"
     :tags  #{" "}
     :props '([:title "2020-08-02"]
              [:id "e79bec75-6e54-4ccb-b753-3ec359291355"])
     :id    nil})
  (new-root-property-bucket
    {:name  "item name"
     :tags  #{"hello" "world"}
     :id    nil
     :props {:some "value"
             :id   "and such"
             :urls ["blah" "other blah.com"]}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; body text
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn root-body->lines [body]
  (->> body
       (remove (fn [line]
                 (some-> line :text (string/starts-with? "#+"))))
       body->lines))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; name / status
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status->status-text [status]
  (when status
    (case status
      :status/cancelled   "CANCELLED"
      :status/done        "[X]"
      :status/not-started "[ ]"
      :status/in-progress "[-]"
      ;; anything else clears the status completely
      "")))

(defn headline-name
  [{:keys [status tags name]} level]
  (let [level     (or level 1)
        level-str (apply str (repeat level "*"))
        headline  (str level-str
                       (when status
                         (str " " (status->status-text status)))
                       " " name)]
    (append-tags headline tags)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->lines as headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare item->root-lines)

(defn item->lines
  ([item] (item->lines item (:level item)))
  ([{:keys [props body items] :as item} level]
   (if (= :root level)
     (item->root-lines item)
     (let [headline       (headline-name item level)
           prop-lines     (new-property-bucket props)
           body-lines     (body->lines body)
           children-lines (->> items (mapcat item->lines))]
       (concat
         (conj
           (concat prop-lines body-lines)
           headline)
         children-lines)))))

(comment
  (item->lines {:name "hi" :tags ["next"] :props {:hi :bye}} :root)
  (item->lines {:name "hi" :tags ["next"] :props {:hi :bye}} 3))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->root-lines as full file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO elevate to exposed api temp-buffer support
(defn item->root-lines
  [{:keys [body items] :as item}]
  (let [root-prop-lines (new-root-property-bucket item)
        body-lines      (root-body->lines body)
        children-lines  (->> items (mapcat item->lines))]
    (concat
      root-prop-lines
      body-lines
      children-lines)))

(comment
  (item->root-lines {:name "hi" :tags ["next" "day"] :props {:hi :bye}}))
