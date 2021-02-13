(ns org-crud.doctor
  (:require [org-crud.fs :as fs]
            [org-crud.core :as core]))

(defn ->report-data [f]
  ;; NOTE this is only level-one totals/averages, not for the whole nested item
  ;; TODO improve reporting here - should not have expectations around structure
  (let [name (fs/base-name f)
        items (->> (core/path->nested-item f) :org/items)
        total (count items)
        word-count (when (seq items) (reduce + 0 (map :org/word-count items)))
        average-word-count (when (seq items) (int (/ word-count total)))]
    {:report/name name
     :report/total total
     :report/word-count word-count
     :report/average-word-count average-word-count}))

(comment
  (->report-data "/home/russ/todo/prompts.org"))

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

(defn file->ids [f]
  ;; TODO walk the tree, collecting uniques and duplicates
  (set (:org/id f)))

(defn print-report [& _args]
  (println "Running org-crud doctor!")

  (let [files (org-files)]

    ;; duplicate ids?
    (println "Checking for duplicate uuids across your files!")
    ;; TODO impl as recursive walk
    ;; may want to loop/recur, or create some api for traversing these trees
    ;; NOTE ultimately we'll want to offer an api for updating at each leaf
    ;; e.g. to fix the duplication problem for us by creating new uuids along the walk
    (->> files
         (map
           (fn [f]
             (let [ids (file->ids f)]
               ids)))))
  )

(comment
  (print-report))
