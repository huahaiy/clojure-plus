(ns clojure+.walk-test
  (:require
   [clojure+.walk :as walk]
   [clojure.test :refer [is are deftest testing]]))

(defn bump [form]
  (if (number? form)
    (inc form)
    form))

(deftest test-postwalk
  (are [before after] (= after (walk/postwalk bump before))

    (list 1 2 3 :a "b" nil 4 5 6)
    (list 2 3 4 :a "b" nil 5 6 7)

    [1 2 3 :a "b" nil 4 5 6]
    [2 3 4 :a "b" nil 5 6 7]

    #{1 2 3 :a "b" nil 4 5 6}
    #{2 3 4 :a "b" nil 5 6 7}

    ;; new keys overriding past keys
    {1 "1" 2 "2" 3 "3"}
    {2 "1" 3 "2" 4 "3"}

    {3 "3" 2 "2" 1 "1"}
    {2 "1" 3 "2" 4 "3"}

    {1 2 3 :a "b" 4 nil nil}
    {2 3 4 :a "b" 5 nil nil}

    {:a {:b {:c {:d [1 2 {:e 3}]}}}}
    {:a {:b {:c {:d [2 3 {:e 4}]}}}}))

(deftest test-list
  (are [before after] (= after (walk/postwalk bump before))

    (list 1 2 3 :a "b" nil 4 5 6)
    (list 2 3 4 :a "b" nil 5 6 7)

    '[(or (like ?t ?pat1) (like ?t ?pat2))]
    '[(or (like ?t ?pat1) (like ?t ?pat2))])

  (testing "There is no LazySeq in result"
    (let [lazy? (atom false)
          res   (walk/postwalk
                  #(do (when (instance? clojure.lang.IPending %)
                         (reset! lazy? true))
                       %)
                  (walk/postwalk-replace
                    {'?pat1 "A" '?pat2 "B"}
                    '[(or (like ?t ?pat1) (like ?t ?pat2))]))]
      (is (not @lazy?))
      (is (= res '[(or (like ?t "A") (like ?t "B"))])))))

(deftest test-skip
  (is (= {:a 1, :c 3}
         (walk/postwalk
           (fn [form]
             (if (and (map-entry? form) (= :b (first form)))
               nil
               form))
           {:a 1, :b 2, :c 3}))))

(deftest test-identity
  (are [form] (let [form' form]
                (identical? form' (walk/postwalk identity form')))
    [1 2 3]
    {:a 1, :b 2}
    (list 1 2 3)
    #{:a :b :c}))

(deftest t-prewalk-replace
  (is (= (walk/prewalk-replace {:a :b} [:a {:a :a} (list 3 :c :a)])
        [:b {:b :b} (list 3 :c :b)])))

(deftest t-postwalk-replace
  (is (= (walk/postwalk-replace {:a :b} [:a {:a :a} (list 3 :c :a)])
        [:b {:b :b} (list 3 :c :b)])))

(deftest t-stringify-keys
  (is (= (walk/stringify-keys {:a 1, nil {:b 2 :c 3}, :d 4})
        {"a" 1, nil {"b" 2 "c" 3}, "d" 4})))

(deftest t-prewalk-order
  (is (= (let [a (atom [])]
           (walk/prewalk (fn [form] (swap! a conj form) form)
             [1 2 {:a 3} (list 4 [5])])
           @a)
        [[1 2 {:a 3} (list 4 [5])]
         1 2 {:a 3} [:a 3] :a 3 (list 4 [5])
         4 [5] 5])))

(deftest t-postwalk-order
  (is (= (let [a (atom [])]
           (walk/postwalk (fn [form] (swap! a conj form) form)
             [1 2 {:a 3} (list 4 [5])])
           @a)
        [1 2
         :a 3 [:a 3] {:a 3}
         4 5 [5] (list 4 [5])
         [1 2 {:a 3} (list 4 [5])]])))

(defrecord Foo [a b c])

; Checks that walk returns the correct result and type of collection
(deftest test-walk
  (let [colls ['(1 2 3)
               [1 2 3]
               #{1 2 3}
               (sorted-set-by > 1 2 3)
               {:a 1, :b 2, :c 3}
               (sorted-map-by > 1 10, 2 20, 3 30)
               (->Foo 1 2 3)
               (map->Foo {:a 1 :b 2 :c 3 :extra 4})]]
    (doseq [c colls]
      (let [walked (walk/walk identity identity c)]
        (is (= c walked))
        (if (map? c)
          (is (= (walk/walk #(update-in % [1] inc) #(reduce + (vals %)) c)
                (reduce + (map (comp inc val) c))))
          (is (= (walk/walk inc #(reduce + %) c)
                (reduce + (map inc c)))))
        #?(:clj
           (when (instance? clojure.lang.Sorted c)
             (is (= (.comparator ^clojure.lang.Sorted c)
                   (.comparator ^clojure.lang.Sorted walked)))))))))

; Checks that walk preserves the MapEntry type. See CLJ-2031.
(deftest walk-mapentry
  (let [coll [:html {:a ["b" 1]} ""]
        f (fn [e] (if (and (vector? e) (not (map-entry? e))) (apply list e) e))]
    (is (= (list :html {:a (list "b" 1)} "") (walk/postwalk f coll)))))

(defrecord RM [a])

(deftest retain-meta
  (let [m {:foo true}]
    (are [o] (= m (meta (walk/postwalk bump (with-meta o m))))
      '(1 2)
      [1 2]
      #{1 2}
      {1 2}
      (map inc (range 3))
      (->RM 1))))
