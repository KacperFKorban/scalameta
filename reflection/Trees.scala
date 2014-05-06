package scala.reflect
package core

import org.scalareflect.invariants._
import org.scalareflect.adt._
import org.scalareflect.errors._
import org.scalareflect.annotations._

/*
list of ugliness discovered so far
1. function parameters can not be annotated
2. try expr catch expr syntax
3. package objects don't have annotations
4. lambdas can not be disambiguated from self type in template
5. patterns in vals are parsed inconsistently
7. awkward meaning of infix patterns: `a infix (b, c)` means `infix(a, b, c)`
8. pq"_: F[_]" is something completely different from pq"_: F[_ >: lo <: hi]"
9. pq"a[b]" is a legal pattern
10. guess what pq"a -1" means
11. no way to fully qualify things that are in empty package
12. vars with default values may not contain patterns
13. constr block
14. q"def x { case 1 => 2 }"
15. q"trait F[T] <: Sobaka[T] with Balabaka { def bazinga } "
*/

// TODO: collection-like methods (see http://clang.llvm.org/docs/LibASTMatchersReference.html)
// TODO: rewriting/transformation methods
// TODO: parser
// TODO: unhygienic quasiquotes
// TODO: consider adding default values for case class fields whenever applicable
// TODO: prettyprinter
// TODO: implement srewrite with palladium
// TODO: implement scaladoc with palladium
// TODO: add moar requires
// TODO: invariants: tree should either have at least one non-trival token or be eq to it's empty value
// TODO: converter: double check conversion of `(f _)(x)` (bug 46)

@root trait Tree {
  // TODO: we also need some sort of host-specific metadata in trees
  implicit def src: SourceContext
  def owner: Scope = parent match { case owner: Scope => owner; case tree => tree.owner }
  def parent: Tree = ??? // TODO: what's the semantics of this?
  def typecheck: List[Attribute] = ??? // TODO: what's the semantics of this?
  // TODO: hygienic equality
}

@branch trait Ref extends Tree

@branch trait Ident extends Ref {
  def value: String
  def isBackquoted: Boolean
}

@branch trait Term extends Arg with Stmt.Template with Stmt.Block {
  def tpe: Type = ???
}
object Term {
  @branch trait Ref extends Term with core.Ref {
    def defn: Member.Overloaded[Member.Term] = ???
  }
  @ast class This(qual: Option[core.Ident]) extends Ref
  @ast class Ident(value: scala.Predef.String @nonEmpty, isBackquoted: Boolean = false) extends core.Ident with Ref with Pat with Member with Member.Val with Member.Var {
    // TODO: require(!keywords.contains(value) || isBackquoted)
    // TODO: if not backquoted, then not all values should be allowed
    def ident: Ident = this
    def mods: List[Mod] = Nil
    override def tpe: Type = ???
  }
  @ast class SuperSelect(qual: Option[core.Ident], supertpe: Option[Type.Ident], selector: Term.Ident) extends Ref
  @ast class Select(qual: Term, selector: Term.Ident) extends Ref with Pat {
    override def tpe: Type = ???
  }

  @ast class Interpolate(prefix: Ident, parts: List[Lit.String] @nonEmpty, args: List[Term]) extends Term {
    // TODO: require(prefix.isInterpolationId)
    require(parts.length == args.length + 1)
  }
  @ast class Apply(fun: Term, args: List[Arg]) extends Term
  @ast class ApplyType(fun: Term, args: List[Type] @nonEmpty) extends Term
  @ast class ApplyRight(lhs: Term, op: Ident, targs: List[Type], rhs: Term) extends Term {
    // TODO: require(op.isRightAssocOp)
  }
  @ast class ApplyUnary(op: Ident, arg: Term) extends Term {
    // TODO: require(op.isUnaryOp)
  }
  @ast class Assign(lhs: Term.Ref, rhs: Term) extends Term
  @ast class Update(lhs: Apply, rhs: Term) extends Term
  @ast class Return(expr: Term) extends Term
  @ast class Throw(expr: Term) extends Term
  @ast class Ascribe(expr: Term, ascribedTpe: Type) extends Term
  @ast class Annotate(expr: Term, annots: List[Mod.Annot] @nonEmpty) extends Term with Has.Mods {
    def mods: List[Mod] = annots
  }
  @ast class Tuple(elements: List[Term] @nonEmpty) extends Term
  @ast class Block(stats: List[Stmt.Block]) extends Term with Scope {
    require(stats.collect{ case v: Defn.Var => v }.forall(_.rhs.isDefined))
  }
  @ast class If(cond: Term, thenp: Term, elsep: Option[Term]) extends Term
  @ast class Match(scrut: Term, cases: Cases) extends Term
  @ast class Try(expr: Term, catchp: Option[Term], finallyp: Option[Term]) extends Term
  @ast class Function(params: List[Aux.Param], body: Term) extends Term with Scope.Params {
    require(params.forall(_.default.isEmpty))
    require(params.exists(_.mods.contains(Mod.Implicit)) ==> (params.length == 1))
  }
  @ast class Cases(cases: List[Aux.Case]) extends Term {
    def isPartialFunction = !parent.isInstanceOf[Match]
  }
  @ast class While(expr: Term, body: Term) extends Term
  @ast class Do(body: Term, expr: Term) extends Term
  @ast class For(enums: List[Enum] @nonEmpty, body: Term) extends Term with Scope {
    require(enums.head.isInstanceOf[Enum.Generator])
  }
  @ast class ForYield(enums: List[Enum] @nonEmpty, body: Term) extends Term
  @ast class New(templ: Aux.Template) extends Term
  // (Denys) TODO: might need additional validation
  @ast class Placeholder() extends Term
  @ast class Eta(term: Term) extends Term
}

@branch trait Type extends Tree with Scope.Template {
  def <:<(other: Type): Boolean = ???
  def weak_<:<(other: Type): Boolean = ???
  def widen: Type = ???
  def dealias: Type = ???
  def erasure: Type = ???
  def companion: Type.Ref = ???
  // TODO: directSuperclasses and others
  override def superclasses: List[Member.Template] = ???
  override def supertypes: List[Type] = ???
  override def self: Aux.Self = ???
  def subclasses: List[Member.Template] = ???
  def inferImplicit: Term = ??? // TODO: typecheck q"implicitly[$this]" and fetch the resulting implicit argument
  // TODO: simple type validation
}
object Type {
  @branch trait Ref extends Type with core.Ref {
    def defn: Member = ???
  }
  @ast class Ident(value: String @nonEmpty, isBackquoted: Boolean = false) extends core.Ident with Ref {
    // TODO: require(keywords.contains(value) ==> isBackquoted)
  }
  @ast class Select(qual: Term.Ref, ident: Type.Ident) extends Ref {
    // TODO: require(qual.isPath)
  }
  @ast class SuperSelect(qual: Option[core.Ident], supertpe: Option[Type.Ident], selector: Type.Ident) extends Ref
  @ast class Project(qual: Type, ident: Type.Ident) extends Ref
  @ast class Singleton(ref: Term.Ref) extends Ref {
    // TODO: require(ref.isPath)
  }
  @ast class Apply(tpe: Type, args: List[Type] @nonEmpty) extends Type
  @ast class Function(params: List[Type], res: Type) extends Type
  @ast class Tuple(elements: List[Type] @nonEmpty) extends Type
  @ast class Compound(tpes: List[Type], refinement: List[Stmt.Refine]) extends Type with Scope.Refine {
    require(tpes.length == 1 ==> hasExplicitRefinement)
    def hasExplicitRefinement: Boolean = ??? // TODO: trivia
  }
  @ast class Existential(tpe: Type, quants: List[Stmt.Existential] @nonEmpty) extends Type with Scope.Existential
  @ast class Annotate(tpe: Type, annots: List[Mod.Annot] @nonEmpty) extends Type with Has.Mods {
    def mods: List[Mod] = annots
  }
  // (Denys) TODO: need to validate that placeholder appears within one of allowed contexts (e.g. `type T = _` is illegal)
  @ast class Placeholder(bounds: Aux.TypeBounds) extends Type
}

@branch trait Pat extends Tree {
  def tpe: Type = ???
}
object Pat {
  @ast class Wildcard() extends Pat
  @ast class SeqWildcard() extends Pat
  @ast class Bind(lhs: Term.Ident, rhs: Pat) extends Pat
  @ast class Alternative(lhs: Pat, rhs: Pat) extends Pat
  @ast class Tuple(elements: List[Pat] @nonEmpty) extends Pat
  @ast class Extract(ref: Term.Ref, targs: List[Type], elements: List[Pat]) extends Pat {
    // TODO: require(ref.isStableId)
  }
  @ast class Interpolate(prefix: Term.Ident, parts: List[Lit.String] @nonEmpty, args: List[Pat]) extends Pat {
    // TODO: require(prefix.isInterpolationId)
    require(parts.length == args.length + 1)
  }
  @ast class Typed(lhs: Pat, rhs: Type) extends Pat {
    require(lhs.isInstanceOf[Pat.Wildcard] || lhs.isInstanceOf[Term.Ident])
  }
}

@branch trait Member extends Tree with Has.Mods {
  def name: String = ??? // TODO: discuss how we want to see this method
  def ref: Ref = ??? // TODO: discuss the implementation of this method
  def overrides: List[Member] = ???
  def companion: Member = ???
  def annots: List[Mod.Annot] = ???
  def doc: Option[Mod.Doc] = ???
  def isVal: Boolean = ???
  def isVar: Boolean = ???
  def isDef: Boolean = ???
  def isType: Boolean = ???
  def isClass: Boolean = ???
  def isTrait: Boolean = ???
  def isObject: Boolean = ???
  def isPkg: Boolean = ???
  def isPkgObject: Boolean = ???
  def isJava: Boolean = ??? // TODO: need special trees for Java artifacts
  def isPrivate: Boolean = ???
  def isProtected: Boolean = ???
  def isPublic: Boolean = ???
  def isImplicit: Boolean = ???
  def isFinal: Boolean = ???
  def isSealed: Boolean = ???
  def isOverride: Boolean = ???
  def isCase: Boolean = ???
  def isAbstract: Boolean = ???
  def isCovariant: Boolean = ???
  def isContravariant: Boolean = ???
  def isLazy: Boolean = ???
  def isAbstractOverride: Boolean = ???
  def isMacro: Boolean = ???
  def isByNameParam: Boolean = ???
  def isVarargParam: Boolean = ???
  def isValParam: Boolean = ???
  def isVarParam: Boolean = ???
}
object Member {
  @branch trait Term extends Member {
    override def overrides: List[Member.Term] = ???
    override def companion: Member.Type = ???
  }
  @branch trait Type extends Member {
    override def overrides: List[Member.Type] = ???
    override def companion: Member.Term = ???
  }
  case class Overloaded[+A <: Member](alts: List[A]) {
    def resolve(tpes: core.Type*): A = ???
  }
  @branch trait Field extends Term {
    def ident: core.Term.Ident
    def tpe: core.Type
  }
  @branch trait Val extends Field
  @branch trait Var extends Field
  @branch trait Def extends Term with Stmt.Template with Has.Paramss with Scope.Params {
    def ident: core.Term.Ident
    def tparams: List[Aux.TypeParam]
    def ret: core.Type
  }
  @branch trait AbstractOrAliasType extends Type {
    def ident: core.Type.Ident
    def tparams: List[Aux.TypeParam]
  }
  @branch trait Template extends Member with Stmt.TopLevel with Stmt.Block with Has.Paramss with Scope.Template {
    def ident: core.Ident
    def explicits: List[List[Aux.Param.Named]] = Nil
    def implicits: List[Aux.Param.Named] = Nil
    def tparams: List[Aux.TypeParam] = Nil
    def templ: Aux.Template
    def superclasses: List[Member.Template] = ???
    def supertypes: List[core.Type] = ???
    def subclasses: List[Member.Template] = ???
    def self: Aux.Self = ???
    override def companion: CompanionType = ???
    protected type CompanionType <: Member
  }
}

@branch trait Decl extends Stmt.Template with Stmt.Refine
object Decl {
  @ast class Val(mods: List[Mod],
                 pats: List[Term.Ident] @nonEmpty,
                 decltpe: core.Type) extends Decl with Stmt.Existential with Has.Mods {
    def tpe: core.Type = ???
  }
  @ast class Var(mods: List[Mod],
                 pats: List[Term.Ident] @nonEmpty,
                 decltpe: core.Type) extends Decl with Has.Mods {
    def tpe: core.Type = ???
  }
  @ast class Def(mods: List[Mod],
                 ident: Term.Ident,
                 tparams: List[Aux.TypeParam],
                 explicits: List[List[Aux.Param.Named]],
                 implicits: List[Aux.Param.Named],
                 declret: core.Type) extends Decl with Member.Def {
    require(!isMacro)
    def ret: core.Type = ???
  }
  @ast class Procedure(mods: List[Mod],
                       ident: Term.Ident,
                       tparams: List[Aux.TypeParam],
                       explicits: List[List[Aux.Param.Named]],
                       implicits: List[Aux.Param.Named]) extends Decl with Member.Def {
    def ret: core.Type = ???
  }
  @ast class Type(mods: List[Mod],
                  ident: core.Type.Ident,
                  tparams: List[Aux.TypeParam],
                  bounds: Aux.TypeBounds) extends Decl with Stmt.Existential with Member.AbstractOrAliasType
}

@branch trait Defn extends Stmt.Block with Stmt.Template
object Defn {
  @ast class Val(mods: List[Mod],
                 pats: List[Pat] @nonEmpty,
                 decltpe: Option[core.Type],
                 rhs: Term) extends Defn with Has.Mods {
    def tpe: core.Type = ???
  }
  @ast class Var(mods: List[Mod],
                 pats: List[Pat] @nonEmpty,
                 decltpe: Option[core.Type],
                 rhs: Option[Term]) extends Defn with Has.Mods {
    require(rhs.nonEmpty || pats.forall(_.isInstanceOf[Term.Ident]))
    require(decltpe.nonEmpty || rhs.nonEmpty)
    def tpe: core.Type = ???
  }
  @ast class Def(mods: List[Mod],
                 ident: Term.Ident,
                 tparams: List[Aux.TypeParam],
                 explicits: List[List[Aux.Param.Named]],
                 implicits: List[Aux.Param.Named],
                 declret: Option[core.Type],
                 body: Term) extends Defn with Member.Def {
    require(isMacro ==> declret.nonEmpty)
    def ret: core.Type = ???
  }
  @ast class Procedure(mods: List[Mod],
                       ident: Term.Ident,
                       tparams: List[Aux.TypeParam],
                       explicits: List[List[Aux.Param.Named]],
                       implicits: List[Aux.Param.Named],
                       body: Term.Block) extends Defn with Member.Def {
    def ret: core.Type = ???
  }
  @ast class Type(mods: List[Mod],
                  ident: core.Type.Ident,
                  tparams: List[Aux.TypeParam],
                  body: core.Type) extends Defn with Stmt.Refine with Member.AbstractOrAliasType
  @ast class Class(mods: List[Mod],
                   ident: core.Type.Ident,
                   override val tparams: List[Aux.TypeParam],
                   declctor: Ctor.Primary,
                   templ: Aux.Template) extends Defn with Member.Template with Member.Type {
    override def ctor: Ctor.Primary = ???
    protected type CompanionType = Object
    override def companion: CompanionType = ???
  }
  @ast class Trait(mods: List[Mod],
                   ident: core.Type.Ident,
                   override val tparams: List[Aux.TypeParam],
                   templ: Aux.Template) extends Defn with Member.Template with Member.Type {
    require(templ.parents.forall(_.argss.isEmpty))
    def isInterface: Boolean = templ.stats.forall(_.isInstanceOf[Decl])
    protected type CompanionType = Object
    override def companion: CompanionType = ???
  }
  @ast class Object(mods: List[Mod],
                    ident: Term.Ident,
                    templ: Aux.Template) extends Defn with Member.Template with Member.Term {
    protected type CompanionType = Member.Template with Member.Type
    override def companion: CompanionType = ???
  }
}

@branch trait Pkg extends Tree {
  def stats: Seq[Stmt.TopLevel]
}
object Pkg {
  @ast class Root(stats: Seq[Stmt.TopLevel]) extends Pkg with Scope.TopLevel
  @ast class Empty(stats: Seq[Stmt.TopLevel]) extends Pkg with Scope.TopLevel
  @ast class Named(override val ref: Term.Ref,
                   stats: Seq[Stmt.TopLevel]) extends Pkg with Stmt.TopLevel with Scope.TopLevel with Member.Term {
    // TODO: require(ref.isQualId)
    def mods: List[Mod] = Nil
  }
  @ast class Object(mods: List[Mod],
                    ident: Term.Ident,
                    templ: Aux.Template) extends Tree with Stmt.TopLevel with Member.Template with Member.Term {
    protected type CompanionType = Member.Template with Member.Type
    override def companion: CompanionType = ???
  }
}

// TODO: how do we ref a constructor?
@branch trait Ctor extends Tree with Has.Mods with Has.Paramss
object Ctor {
  @ast class Primary(mods: List[Mod] = Nil,
                     explicits: List[List[Aux.Param.Named]] = Nil,
                     implicits: List[Aux.Param.Named] = Nil) extends Ctor
  @ast class Secondary(mods: List[Mod] = Nil,
                       explicits: List[List[Aux.Param.Named]] @nonEmpty = List(Nil),
                       implicits: List[Aux.Param.Named] = Nil,
                       primaryCtorArgss: List[List[Arg]] = Nil,
                       stats: List[Stmt.Block] = Nil) extends Ctor with Stmt.Template with Scope.Params
}

object Stmt {
  @branch trait TopLevel extends Tree
  @branch trait Template extends Block
  @branch trait Block extends Refine
  @branch trait Refine extends Existential
  @branch trait Existential extends Tree
}

@branch trait Scope extends Tree {
  def members: Seq[Member] = ???
  def members(name: Ident): Member.Overloaded[Member] = ???
}
object Scope {
  @branch trait TopLevel extends Scope {
    def packages: Seq[Pkg.Named] = ???
    def packages(name: Ident): Pkg.Named = ???
    def packages(name: String): Pkg.Named = ???
    def packages(name: scala.Symbol): Pkg.Named = ???
    def classes: Seq[Defn.Class] = ???
    def classes(name: Ident): Defn.Class = ???
    def classes(name: String): Defn.Class = ???
    def classes(name: scala.Symbol): Defn.Class = ???
    def traits: Seq[Defn.Trait] = ???
    def traits(name: Ident): Defn.Trait = ???
    def traits(name: String): Defn.Trait = ???
    def traits(name: scala.Symbol): Defn.Trait = ???
    def objects: Seq[Defn.Object] = ???
    def objects(name: Ident): Defn.Object = ???
    def objects(name: String): Defn.Object = ???
    def objects(name: scala.Symbol): Defn.Object = ???
    def pkgobject: Pkg.Object = ???
    // TODO: consider extending Template in order to bring contents of package objects in
  }
  @branch trait Template extends Block with Params {
    // TODO: directSuperclasses and others
    def superclasses: List[Member.Template]
    def supertypes: List[Type]
    def self: Aux.Self
    def ctor: Ctor.Primary = ???
    def ctors: Seq[Ctor] = ???
  }
  @branch trait Block extends Refine {
    def classes: Seq[Defn.Class] = ???
    def classes(name: Ident): Defn.Class = ???
    def classes(name: String): Defn.Class = ???
    def classes(name: scala.Symbol): Defn.Class = ???
    def traits: Seq[Defn.Trait] = ???
    def traits(name: Ident): Defn.Trait = ???
    def traits(name: String): Defn.Trait = ???
    def traits(name: scala.Symbol): Defn.Trait = ???
    def objects: Seq[Defn.Object] = ???
    def objects(name: Ident): Defn.Object = ???
    def objects(name: String): Defn.Object = ???
    def objects(name: scala.Symbol): Defn.Object = ???
    def vars: Seq[Member.Var] = ???
    def vars(name: Ident): Member.Var = ???
    def vars(name: String): Member.Var = ???
    def vars(name: scala.Symbol): Member.Var = ???
  }
  @branch trait Refine extends Existential {
    def defs: Seq[Member.Def] = ???
    def defs(name: Ident): Member.Def = ???
    def defs(name: String): Member.Def = ???
    def defs(name: scala.Symbol): Member.Def = ???
  }
  @branch trait Existential extends Scope {
    def vals: Seq[Member.Val] = ???
    def vals(name: Ident): Member.Val = ???
    def vals(name: String): Member.Val = ???
    def vals(name: scala.Symbol): Member.Val = ???
    def types: Seq[Member.AbstractOrAliasType] = ???
    def types(name: Ident): Member.AbstractOrAliasType = ???
    def types(name: String): Member.AbstractOrAliasType = ???
    def types(name: scala.Symbol): Member.AbstractOrAliasType = ???
  }
  @branch trait Params extends Scope {
    // TODO: might be nice to have `params("x")` or `tparams("T")`
    // but I've no idea how to marry this with pre-existing `paramss` and `tparams`
  }
}

@branch trait Lit extends Term with Pat {
  override def tpe: Type = ???
}
object Lit {
  // TODO: maybe add overloaded apply(value)
  // TODO: maybe add unapply(lit): Option[Any]
  @branch trait Bool extends Lit
  @ast class True() extends Bool
  @ast class False() extends Bool
  @ast class Int(value: scala.Int) extends Lit with Type
  @ast class Long(value: scala.Long) extends Lit with Type
  @ast class Float(value: scala.Float) extends Lit with Type
  @ast class Double(value: scala.Double) extends Lit with Type
  @ast class Char(value: scala.Char) extends Lit with Type
  @ast class String(value: Predef.String) extends Lit with Type
  // TODO: not all symbols are representable as literals, e.g. scala.Symbol("")
  @ast class Symbol(value: scala.Symbol) extends Lit with Type
  @ast class Null() extends Lit
  @ast class Unit() extends Lit
}

@ast class Import(clauses: List[Import.Clause] @nonEmpty) extends Stmt.TopLevel with Stmt.Template with Stmt.Block
object Import {
  // TODO: validate that wildcard import can only be the last one in the list of sels
  @ast class Clause(ref: Term.Ref, sels: List[Selector] @nonEmpty) extends Tree {
    // TODO: require(ref.isStableId)
  }

  @branch trait Selector extends Tree
  object Selector {
    @ast class Wildcard() extends Selector
    // TODO: needs some kind of idents here but they can neither be term nor type
    @ast class Name(name: String) extends Selector
    @ast class Rename(from: String, to: String) extends Selector
    @ast class Unimport(name: String) extends Selector
  }
}

@branch trait Arg extends Tree
object Arg {
  @ast class Named(name: Term.Ident, arg: Term) extends Arg
  @ast class Seq(arg: Term) extends Arg
}

@branch trait Enum extends Tree
object Enum {
  @ast class Generator(pat: Pat, rhs: Term) extends Enum
  @ast class Val(pat: Pat, rhs: Term) extends Enum
  @ast class Guard(cond: Term) extends Enum
}

@branch trait Mod extends Tree
object Mod {
  @ast class Annot(tpe: Type, argss: List[List[Arg]]) extends Mod
  @ast class Doc(doc: String) extends Mod // TODO: design representation for scaladoc
  @ast class Private(within: String) extends Mod // TODO: design a name resolution API for these and imports
  @ast class Protected(within: String) extends Mod
  @ast class Implicit() extends Mod
  @ast class Final() extends Mod
  @ast class Sealed() extends Mod
  @ast class Override() extends Mod
  @ast class Case() extends Mod
  @ast class Abstract() extends Mod
  @ast class Covariant() extends Mod
  @ast class Contravariant() extends Mod
  @ast class Lazy() extends Mod
  @ast class AbstractOverride() extends Mod
  @ast class Macro() extends Mod
  @ast class ByNameParam() extends Mod
  @ast class VarargParam() extends Mod
  @ast class ValParam() extends Mod
  @ast class VarParam() extends Mod
}

@branch trait Attribute extends Tree
object Attribute {
  @ast class Ref(ref: core.Ref) extends Attribute
  @ast class Type(tpe: core.Type) extends Attribute
  @ast class InferredTargs(targs: List[core.Type]) extends Attribute
  @ast class InferredVargs(vargs: List[core.Term]) extends Attribute
  @ast class MacroExpansion(tree: core.Tree) extends Attribute
}

object Aux {
  @ast class Case(pat: Pat, cond: Option[Term] = None, body: Option[Term] = None) extends Tree with Scope
  @ast class Parent(tpe: Type, argss: List[List[Arg]] = Nil) extends Ref
  @ast class Template(early: List[Defn.Val] = Nil, parents: List[Parent] = Nil,
                      declself: Self = Self.empty, stats: Seq[Stmt.Template] = Nil) extends Tree with Scope.Template {
    require(parents.isEmpty || !parents.tail.exists(_.argss.nonEmpty))
    def tpe: Type = ???
    def superclasses: List[Member.Template] = ???
    def supertypes: List[Type] = ???
    def self: Self = ???
    def hasExplicitStats: Boolean = ??? // TODO: trivia
  }
  @ast class Self(ident: Option[Term.Ident] = None, decltpe: Option[Type] = None) extends Member.Term {
    def mods: List[Mod] = Nil
    def tpe: Type = ???
  }
  @branch trait Param extends Tree with Has.Mods {
    def tpe: Type = ???
    def default: Option[Term]
  }
  object Param {
    @ast class Anonymous(decltpe: Option[Type] = None,
                         mods: List[Mod] = Nil,
                         default: Option[Term] = None) extends Param
    @ast class Named(ident: Term.Ident,
                     decltpe: Type,
                     mods: List[Mod] = Nil,
                     default: Option[Term] = None) extends Param with Member.Term
  }
  @branch trait TypeParam extends Tree with Has.Mods {
    def tparams: List[Aux.TypeParam]
    def contextBounds: List[core.Type]
    def viewBounds: List[core.Type]
    def bounds: Aux.TypeBounds
  }
  object TypeParam {
    @ast class Anonymous(mods: List[Mod] = Nil,
                         tparams: List[Aux.TypeParam] = Nil,
                         contextBounds: List[core.Type] = Nil,
                         viewBounds: List[core.Type] = Nil,
                         bounds: Aux.TypeBounds = Aux.TypeBounds.empty) extends TypeParam
    @ast class Named(ident: core.Type.Ident,
                     mods: List[Mod] = Nil,
                     tparams: List[Aux.TypeParam] = Nil,
                     contextBounds: List[core.Type] = Nil,
                     viewBounds: List[core.Type] = Nil,
                     bounds: Aux.TypeBounds = Aux.TypeBounds.empty) extends TypeParam with Member.Type
  }
  @ast class TypeBounds(lo: Option[Type] = None, hi: Option[Type] = None) extends Tree
}

object Has {
  @branch trait Mods extends Tree {
    def mods: List[Mod]
    // (Eugene) TODO: https://docs.google.com/spreadsheet/ccc?key=0Ahw_zqMtW4nNdC1lRVJvc3VjTUdOX0ppMVpSYzVRSHc&usp=sharing#gid=0
    // * write a script that fetches this google doc and converts it into a, say, CSV spec
    // * write a test that validates the spec by generating source files and parsing them
    // * write a macro that generates implementation of validateAnnots from the spec + extension methods like isImplicit
    private[core] def validateMods(): Either[Exception, Unit] = ???
  }

  @branch trait Paramss extends Tree {
    def explicits: List[List[Aux.Param.Named]]
    def implicits: List[Aux.Param.Named]
    def paramss: List[List[Aux.Param.Named]] = explicits :+ implicits
  }
}