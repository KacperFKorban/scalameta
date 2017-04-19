package scala.meta
package internal
package semantic
package v1

import scala.meta.semantic.v1._

trait DatabaseOps {
  private[meta] implicit class XtensionInternalDatabase(db1: Database) {
    def append(db2: Database): Database = {
      import scala.collection.mutable
      val names2 = mutable.Map[Anchor, Symbol]()
      names2 ++= db1.names
      val paths = db2.names.keys.map(_.path).toSet
      paths.foreach(path => names2.retain((k, _) => k.path != path))
      names2 ++= db2.names
      val messages = mutable.LinkedHashSet.empty[Message]
      messages ++= db1.messages
      messages ++= db2.messages
      val denotations = db1.denotations ++ db2.denotations
      Database(names2.toMap, messages.toSeq, denotations.toMap)
    }
  }
}