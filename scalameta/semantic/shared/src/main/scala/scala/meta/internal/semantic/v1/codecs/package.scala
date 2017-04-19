package scala.meta.internal.semantic.v1

import scala.meta.semantic.{v1 => m}
import scala.meta.internal.semantic.v1.{proto => p}

package object codecs {
  implicit class XtensionProtoSerializable[A](val a: A) extends AnyVal {
    def toMeta[B](implicit ev: ProtoDecoder[B, A]): B = ev.fromProto(a)
    def toProto[B](implicit ev: ProtoEncoder[A, B]): B = ev.toProto(a)
    def toProtoOpt[B](implicit ev: ProtoEncoder[A, B]): Option[B] = Option(toProto[B])
  }

  implicit val ResolvedNameEncoder: ProtoEncoder[(m.Anchor, m.Symbol), p.ResolvedName] =
    new ProtoEncoder[(m.Anchor, m.Symbol), p.ResolvedName] {
      override def toProto(e: (m.Anchor, m.Symbol)): p.ResolvedName = e match {
        case (m.Anchor(_, start, end), sym) =>
          p.ResolvedName(Option(p.Range(start, end)), sym.syntax)
      }
    }

  implicit val MessageEncoder: ProtoEncoder[m.Message, p.Message] =
    new ProtoEncoder[m.Message, p.Message] {
      override def toProto(e: m.Message): p.Message = e match {
        case m.Message(m.Anchor(addr, start, end), sev, msg) =>
          p.Message(Option(p.Range(start, end)), p.Message.Severity.fromValue(sev.id), msg)
      }
    }

  implicit val SymbolDenotationEncoder: ProtoEncoder[(m.Symbol, m.Denotation), p.SymbolDenotation] =
    new ProtoEncoder[(m.Symbol, m.Denotation), p.SymbolDenotation] {
      override def toProto(e: (m.Symbol, m.Denotation)): p.SymbolDenotation = e match {
        case (sym, denot) =>
          p.SymbolDenotation(sym.syntax, Option(p.Denotation(denot.flags)))
      }
    }

  implicit val DatabaseCodec: ProtoCodec[m.Database, p.Database] =
    new ProtoCodec[m.Database, p.Database] {
      override def toProto(e: m.Database): p.Database = {
        val messagesGrouped = e.messages.groupBy(_.anchor.path).withDefaultValue(Nil)
        val files = e.names
          .groupBy(_._1.path)
          .map {
            case (path, names) =>
              val messages = messagesGrouped(path).map(_.toProto[p.Message])
              p.DatabaseFile(
                path = path.absolute,
                names = names.map(_.toProto[p.ResolvedName]).toSeq,
                messages = messages,
                denotations = e.denotations.map(_.toProto[p.SymbolDenotation]).toSeq
              )
          }
          .toSeq
        p.Database(files)
      }

      override def fromProto(e: p.Database): m.Database = {
        val names = e.files.flatMap {
          case p.DatabaseFile(path, names, _, _) =>
            names.map {
              case p.ResolvedName(Some(p.Range(start, end)), m.Symbol(symbol)) =>
                m.Anchor(path, start, end) -> symbol
            }
          case _ =>
            fail(e)
        }.toMap
        val messages: Seq[m.Message] = e.files.flatMap {
          case p.DatabaseFile(path, _, messages, _) =>
            messages.map {
              case p.Message(Some(p.Range(start, end)), sev, message) =>
                m.Message(m.Anchor(path, start, end), m.Severity.fromId(sev.value), message)
              case e =>
                fail(e)
            }
          case e =>
            fail(e)
        }
        val denotations = e.files.flatMap {
          case p.DatabaseFile(_, _, _, denotations) =>
            denotations.map {
              case p.SymbolDenotation(m.Symbol(symbol), Some(p.Denotation(flags))) =>
                symbol -> m.Denotation(flags)
            }
          case _ =>
            fail(e)
        }.toMap
        m.Database(names, messages, denotations)
      }
    }

  private def fail(e: Any): Nothing = sys.error(s"fail protobuf! $e")
}