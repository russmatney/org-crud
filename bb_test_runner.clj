#!/usr/bin/env bb
;; https://book.babashka.org/#_running_tests

(require '[clojure.test :as t]
         '[babashka.process :as p]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")

;; TODO collect these automagically
(def test-nses
  (->>
    ['org-crud.create-test
     'org-crud.delete-test
     'org-crud.update-test
     'org-crud.core-test
     'org-crud.node-test
     'org-crud.markdown-test
     'org-crud.refile-test]
    (remove nil?)))

(doall
  (for [t test-nses]
    (require t)))

(def test-results
  (apply t/run-tests test-nses))

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)
