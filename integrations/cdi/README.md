# Helidon CDI Integrations

This subproject contains [CDI portable extensions](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi)
 that provide convenient integrations with popular libraries and services.

* [Datasource using HikariCP](datasource-hikaricp): inject [`DataSource`](https://docs.oracle.com/javase/8/docs/api/javax/sql/DataSource.html)
  objects into your CDI-based application that are backed by the
 [Hikari connection pool](http://brettwooldridge.github.io/HikariCP/).

* [Jedis Client](jedis-cdi): inject [Jedis](https://github.com/xetorthio/jedis#jedis)
  client objects into your CDI-based application.
