package enju.ccg.lexicon

import scala.collection.mutable.ListMap
import Direction.Direction

trait NodeLabel {
  def category: Category
}

sealed trait TerminalLabel extends NodeLabel {
  def word: Word
  def baseForm: Word = word
  def pos: PoS
}

case class JapaneseTerminalLabel(
  override val word: Word,
  override val baseForm: Word,
  override val pos: PoS,
  override val category: Category) extends TerminalLabel

// used for many language not using baseForm
case class SimpleTerminalLabel(
  override val word:Word,
  override val pos: PoS,
  override val category: Category) extends TerminalLabel

object TerminalLabel {
  /** Parse string like "(NP[nc,nm]1／NP[nc,nm]1)＼NP[nc,nm] の/の/助詞-連体化/_"
    */
  def parseJapanese(terminalStr: String, dict: Dictionary) = terminalStr.split(" ") match {
    case a if a.size == 2 => (a(0), a(1)) match {
      case (categoryStr, wordPosStr) => {
        val sep1 = wordPosStr.indexOf('/')
        val sep2 = wordPosStr.indexOf('/', sep1 + 1)

        val surfaceForm = dict.getWordOrCreate(wordPosStr.substring(0, sep1))
        val baseForm = dict.getWordOrCreate(wordPosStr.substring(sep1+1, sep2))
        val pos = dict.getPoSOrCreate(wordPosStr.substring(sep2 + 1))

        val category = dict.getCategoryOrCreate(categoryStr)
        JapaneseTerminalLabel(surfaceForm, baseForm, pos, category)
      }
    }
    case _ => sys.error("invalid form for JapaneseTerminalLabel: " + terminalStr)
  }
  /** Parse string like "L N/N NNP NNP Dutch N_126/N_126"
    */
  def parseSimple(terminalStr: String, dict: Dictionary) = terminalStr.split(" ") match {
    case a if a.size == 6 => (a(1), a(2), a(3), a(4)) match {
      case (categoryStr, modPoSStr, origPoSStr, wordStr) =>
        SimpleTerminalLabel(
          dict.getWordOrCreate(wordStr),
          dict.getPoSOrCreate(origPoSStr),
          dict.getCategoryOrCreate(categoryStr))
    }
    case _ => sys.error("invalid form for SimpleTerminalLabel: " + terminalStr)
  }
}

// TODO: specify ruleType with objects
case class NonterminalLabel(
  head: Direction,
  override val category: Category,
  ruleType: String) extends NodeLabel

object NonterminalLabel {
  /** Parse string like "< NP[ga,nm]"
    */
  def parseJapanese(nontermStr: String, dict: Dictionary) = nontermStr.split(" ") match {
    case a if a.size == 2 => (a(0), a(1)) match {
      case (ruleStr, categoryStr) =>
        NonterminalLabel(Direction.Left, dict.getCategoryOrCreate(categoryStr), ruleStr)
    }
  }

  /** Parse string like "T S[dcl] 0 2"
    */
  def parseSimple(nontermStr: String, dict: Dictionary) = nontermStr.split(" ") match {
    case a if a.size == 4 => (a(1), a(2)) match {
      case (categoryStr, directionNum) => NonterminalLabel(
        if (directionNum == "0") Direction.Left else Direction.Right,
        dict.getCategoryOrCreate(categoryStr),
        "")
    }
  }
}

trait ParseTreeConverter {
  def dict: Dictionary
  def toLabelTree(stringTree: ParseTree[String]): ParseTree[NodeLabel] = stringTree.mapBottomup {
    node => node match {
      case _: LeafTree[_] => parseTerminalLabel(node.label)
      case _ => parseNonterminalLabel(node.label)
    }
  }
  protected def parseTerminalLabel(terminalStr: String): TerminalLabel
  protected def parseNonterminalLabel(nontermStr: String): NonterminalLabel

  def toSentenceFromStringTree(stringTree: ParseTree[String]): GoldSuperTaggedSentence = {
    val terminalSeq = stringTree.getSequence.map { node => parseTerminalLabel(node.label) }
    terminalSeqToSentence(terminalSeq)
  }
  def terminalSeqToSentence(terminalSeq: Seq[TerminalLabel]) = new GoldSuperTaggedSentence(
    terminalSeq.map(_.word),
    terminalSeq.map(_.baseForm),
    terminalSeq.map(_.pos),
    terminalSeq.map(_.category))

  def toSentenceFromLabelTree(labelTree: ParseTree[NodeLabel]): GoldSuperTaggedSentence = {
    val terminalSeq = labelTree.getSequence.map { _.label match {
      case terminal: TerminalLabel => terminal
      case _ => sys.error("Labels of leaf nodes of objTree must all be TerminalLabel")
    }}
    terminalSeqToSentence(terminalSeq)
  }
  def toDerivation(labelTree: ParseTree[NodeLabel]): Derivation = {
    def extractSpan(span: Option[Span]): (Int, Int) = span match {
      case Some(Span(b, e)) => (b, e)
      case _ => sys.error("oops. None span is going to be extracted!")
    }
    def ruleType(label: NodeLabel) = label match {
      case nonterm: NonterminalLabel => nonterm.ruleType
      case _ => sys.error("Type error in NodeLabel.")
    }

    labelTree.setSpans(0)
    val length = labelTree.span match {
      case Some(Span(0, j)) => j
      case _ => sys.error("setSpans error.")
    }
    val derivationMap = Array.fill(length + 1)(Array.fill(length + 1)(new ListMap[Category, AppliedRule]))

    def setDerivationMap(tree :ParseTree[NodeLabel]): AppliedRule = tree match {
      case LeafTree(_) => AppliedRule(NoneChildPoint(), "") // with empty rule
      case UnaryTree(child, label) =>
        val (childBegin, childEnd) = extractSpan(child.span)
        derivationMap(childBegin)(childEnd) += child.label.category -> setDerivationMap(child)
        AppliedRule(UnaryChildPoint(Point(childBegin, childEnd, child.label.category)), ruleType(label))
      case BinaryTree(left, right, label) =>
        val (leftBegin, leftEnd) = extractSpan(left.span)
        val (rightBegin, rightEnd) = extractSpan(right.span)

        derivationMap(leftBegin)(leftEnd) += left.label.category -> setDerivationMap(left)
        derivationMap(rightBegin)(rightEnd) += right.label.category -> setDerivationMap(right)
        AppliedRule(
          BinaryChildrenPoints(
            Point(leftBegin, leftEnd, left.label.category),
            Point(rightBegin, rightEnd, right.label.category)),
          ruleType(label))
    }

    derivationMap(0)(length) += labelTree.label.category -> setDerivationMap(labelTree)
    Derivation(derivationMap, Array(Point(0, length, labelTree.label.category)))
  }
}

class JapaneseParseTreeConverter(override val dict: Dictionary) extends ParseTreeConverter {
  override def parseTerminalLabel(terminalStr: String) = TerminalLabel.parseJapanese(terminalStr, dict)
  override def parseNonterminalLabel(nontermStr: String) = NonterminalLabel.parseJapanese(nontermStr, dict)
}

class EnglishParseTreeConverter(override val dict: Dictionary) extends ParseTreeConverter {
  override def parseTerminalLabel(terminalStr: String) = TerminalLabel.parseSimple(terminalStr, dict)
  override def parseNonterminalLabel(nontermStr: String) = NonterminalLabel.parseSimple(nontermStr, dict)
}
