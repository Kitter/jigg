package enju.util

import java.io._
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

object IOUtil {
  def openBinIn(path: String): ObjectInputStream =
    new ObjectInputStream(new BufferedInputStream(inStream(path)))

  def openIn(path: String): BufferedReader = bufReader(inStream(path))

  def openStandardIn: BufferedReader = bufReader(System.in)

  def inStream(path: String) = path match {
    case gzipped if gzipped.endsWith(".gz") => new GZIPInputStream(new FileInputStream(gzipped))
    case file => new FileInputStream(file)
  }

  def bufReader(stream: InputStream) = new BufferedReader(new InputStreamReader(stream))

  def openIterator(path: String): Iterator[String] = inputIterator(openIn(path))
  def openStandardIterator: Iterator[String] = inputIterator(openStandardIn)
  def inputIterator(reader: BufferedReader) = Iterator.continually(reader.readLine()).takeWhile(_ != null)
}
