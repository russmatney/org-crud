(ns org-crud.doctor
  (:require [org-crud.fs :as fs]
            [org-crud.core :as core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; org helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn org-files
  "Fetches org-files.

  Only recurses one layer, collecting roughly todo/*, todo/garden/*."
  []
  (let [org-dir-path "/home/russ/todo"]
    (->> org-dir-path
         fs/list-dir
         (mapcat (fn [f]
                   (if (fs/directory? f)
                     (fs/list-dir f)
                     [f])))
         (remove fs/directory?)
         (filter (comp #{".org"} fs/extension)))))

(comment
  (count (org-files)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; duplicate ids
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn global-uuid-duplicates [fs]
  (let [items (->> fs (mapcat core/path->flattened-items))]
    (println "Checking for global uuids over " (count fs) " files and " (count items) " items.")
    (loop [remaining-items items
           checked-ids     #{}]
      (let [item (first remaining-items)
            id   (:org/id item)]
        (when (checked-ids id)
          (println "duplicate uuid found in file " (:org/source-file item) ".")
          ;; (println "item: " item)
          (println "id: " id)
          (println "name: " (:org/name item)))
        (when (seq remaining-items)
          (recur (rest remaining-items) (conj checked-ids id)))))))

(comment
  (global-uuid-duplicates ["/home/russ/todo/prompts.org"]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; cli interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-report [& _args]
  (println "Running org-crud doctor!")

  (let [file-paths (org-files)]
    (println "checking for global uuid duplicates")
    (global-uuid-duplicates file-paths)

    (println "Checking for broken org-links in your files")
    (println "TODO: impl broken link fixer")

    ))


(comment
  (print-report))
