(defproject org.clojars.huahaiy/clojure-plus "1.1.1"
  :description "A collection of utilities that improve Clojure experience"
  :license     {:name "MIT" :url "https://github.com/tonsky/clojure-plus/blob/master/LICENSE"}
  :url         "https://github.com/tonsky/clojure-plus"
  :dependencies
  [[org.clojure/clojure "1.12.0"]]
  :deploy-repositories [["clojars" {:url           "https://repo.clojars.org"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password
                                    :sign-releases false}]])
