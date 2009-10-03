package mr.go.coroutines.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import mr.go.coroutines.tests.classes.*;
import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.CoroutineClosedException;

import org.junit.BeforeClass;
import org.junit.Test;

public class CoroutineTests {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Logger numberGeneratorLogger = Logger
				.getLogger("mr.go.coroutines.tests.classes.NumberGenerator");
		Logger bettingLogger = Logger
				.getLogger("mr.go.coroutines.tests.classes.Betting");
		numberGeneratorLogger.setLevel(Level.ALL);
		bettingLogger.setLevel(Level.ALL);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCategory2Variables() {
		Betting bet = new Betting();
		CoIterator<Boolean, Double> betting = bet.accept();
		betting.send(0.5);
		boolean sureWin = betting.send(1.0);
		assertTrue(sureWin);
		boolean sureLoss = betting.send(0.0);
		assertFalse(sureLoss);
		betting.send(1.5);
	}

	@Test
	public void testCategory2VariablesOnStack() {
		Betting bet = new Betting();
		CoIterator<Boolean, Double> betting = bet.accept(0.1);
		betting.send(0.5);
		boolean sureLoss = betting.send(5d);
		assertFalse(sureLoss);
	}

	@Test
	public void testClassicSameFringe() {
		Tree<Integer> tree1 = new Tree<Integer>(1);
		tree1.insert(8);
		tree1.insert(6);
		tree1.insert(3);
		tree1.insert(9);
		Tree<Integer> tree2 = new Tree<Integer>(6);
		tree2.insert(1);
		tree2.insert(3);
		tree2.insert(8);
		tree2.insert(9);
		assertTrue(Tree.sameFringe(tree1, tree2));
		Tree<Integer> tree3 = new Tree<Integer>(1);
		tree3.insert(10);
		tree3.insert(2);
		tree3.insert(9);
		tree3.insert(4);
		assertFalse(Tree.sameFringe(tree1, tree3));
	}

	@Test
	public void testEach() {
		int[] fibs = new int[13];
		int i = 0;
		for (int fib : NumberGenerator.fibonacciNumbers().till(13)) {
			fibs[i++] = fib;
		}
		assertArrayEquals(new int[]
		{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144 }, fibs);
	}

	@Test
	public void testExceptionHandler() throws IOException {
		String file = "src/mr/go/coroutines/tests/lines.txt";
		CoIterator<String, Void> lines = Grep.grep("Java", file);
		String foundLine = lines.next();
		assertEquals("Java rocks!!!", foundLine);
		lines.close();
	}

	@Test
	public void testFinallyBlock() {
		Grep grep = new Grep("Java");
		LineCounter lineCount = new LineCounter();
		CoIterator<Void, String> grepPipe = grep.grep(lineCount.greppedLines());
		grepPipe.send("Hello");
		grepPipe.send("I want to say");
		grepPipe.send("that");
		grepPipe.send("Java rocks!!!");
		grepPipe.close();
		assertTrue(lineCount.isClosed());
		assertEquals(1, lineCount.getCount());

	}

	@Test(expected = NoSuchElementException.class)
	public void testForLoop() {
		NumberGenerator numberGenerator = new NumberGenerator(0);
		CoIterator<Integer, Void> numbers = numberGenerator.getNumbers(10);
		int startingNumber = numberGenerator.getStartingNumber();
		for (int i = 0; i < 1000; i++) {
			Integer number = numbers.next();
			assertEquals(startingNumber + i, number.intValue());
		}
	}

	@Test(expected = NoSuchElementException.class)
	public void testFramesMerge1() {
		PeopleDb db = new PeopleDb(Arrays.asList(
				"John",
				"Natasha",
				"Rob",
				"Edward"));
		CoIterator<String, String> peopleIt = db.people();
		assertEquals("John", peopleIt.next());
		assertEquals("Natasha", peopleIt.next());
		assertEquals("Rob", peopleIt.next());
		assertEquals("Edward", peopleIt.next());
		assertEquals("John", peopleIt.next());
		peopleIt.send("add:Mary");
		peopleIt.send("add:George");
		assertEquals("Edward", peopleIt.next());
		assertEquals("Mary", peopleIt.next());
		peopleIt.send("remove:George");
		peopleIt.send("remove:John");
		peopleIt.send("remove:Natasha");
		peopleIt.send("remove:Rob");
		peopleIt.send("remove:Edward");
		peopleIt.send("remove:Mary");
		peopleIt.next();
	}

	@Test(expected = NoSuchElementException.class)
	public void testFramesMerge2() {
		TelegramReceiver tg = new TelegramReceiver(12);
		List<Telegram> telegrams = new LinkedList<Telegram>();
		CoIterator<Void, String> telegramStream = tg.receiver(telegrams);
		telegramStream.send("Hello");
		telegramStream.send("STOP");
		telegramStream.send("How");
		telegramStream.send("STOP");
		telegramStream.send("do");
		telegramStream.send("STOP");
		telegramStream.send("you");
		telegramStream.send("STOP");
		telegramStream.send("do?");
		telegramStream.send("ZZZZZ");
		telegramStream.send("Hello");
		telegramStream.send("STOP");
		telegramStream.send("Constantinopole");
		telegramStream.send("STOP");
		telegramStream.send("is");
		telegramStream.send("STOP");
		telegramStream.send("lost!!!");
		telegramStream.send("ZZZZZ");
		telegramStream.send("ZZZZZ");
		telegramStream.send(" ");
		Telegram[] telegramsArray = telegrams.toArray(new Telegram[0]);
		Telegram t1 = telegramsArray[0];
		Telegram t2 = telegramsArray[1];
		assertEquals("Hello How do you do?", t1.getMessage());
		assertFalse(t1.isHasOverlengthWord());
		assertEquals(5, t1.getWordCount());
		assertEquals("Hello Constantipol is lost!!!", t2.getMessage());
		assertTrue(t2.isHasOverlengthWord());
		assertEquals(4, t2.getWordCount());
	}

	@Test
	public void testNestedExceptionHandler() throws IOException {
		String file = "src/mr/go/coroutines/tests/grepped_lines.txt";
		Grep grep = new Grep("Java");
		LineWriter lineWriter = new LineWriter(file);
		CoIterator<Void, String> grepPipe = grep
				.grep(lineWriter.greppedLines());
		grepPipe.send("Hello");
		grepPipe.send("I want to say");
		grepPipe.send("that");
		grepPipe.send("Java rocks!!!");
		grepPipe.close();
		FileReader fr = new FileReader(file);
		BufferedReader reader = new BufferedReader(fr);
		try {
			assertEquals("Java rocks!!!", reader.readLine());
			assertEquals("END", reader.readLine());
		} finally {
			reader.close();
		}
	}

	@Test
	public void testNestedLoops() {
		StringReceiver receiver1 = new StringReceiver();
		String str = "Hello world!";
		byte[] bytes = str.getBytes();
		List<String> receivedStrings = new LinkedList<String>();
		CoIterator<Void, Byte> decoder = Decoder.decode(receiver1
				.receive(receivedStrings));
		decoder.send(Decoder.HEADER);
		decoder.send((byte) bytes.length);
		for (int i = 0; i < bytes.length; i++) {
			decoder.send(bytes[i]);
		}
		decoder.send(Decoder.FOOTER);
		assertEquals(Arrays.asList(str), receivedStrings);
	}

	@Test
	public void testNonEmptyStackOnYield() {
		Grep grep = new Grep("Java");
		LinePrinter linePrinter = new LinePrinter();
		List<String> lines = new LinkedList<String>();
		CoIterator<Void, String> grepPipe = grep.grep(linePrinter
				.greppedLines(lines));
		grepPipe.send("Hello");
		grepPipe.send("I want to say");
		grepPipe.send("that");
		grepPipe.send("Java rocks!!!");
		grepPipe.close();
		assertTrue(linePrinter.isClosed());
		assertEquals(Arrays.asList("Java rocks!!!"), lines);
	}

	@Test
	public void testRecursion() {
		Tree<Integer> bst = new Tree<Integer>(1);
		bst.insert(8);
		bst.insert(6);
		bst.insert(3);
		bst.insert(9);
		Integer prev = 1;
		for (Integer i : bst.inorder()) {
			assertTrue(prev.compareTo(i) <= 0);
			prev = i;
		}
		assertEquals(9, prev.intValue());
	}

	@Test(expected = CoroutineClosedException.class)
	public void testStaticInfiniteLoopAndClose() {
		CoIterator<Integer, Void> numbers = NumberGenerator.integers();
		for (int i = 0; i < 1000; i++) {
			int integer = numbers.next();
			assertEquals(i, integer);
		}
		numbers.close();
		numbers.next();
	}

	@Test
	public void testTemporaryVariables() {
		Double[] fareySequence_8 = new Double[]
		{ 0d, 1 / 8d, 1 / 7d, 1 / 6d, 1 / 5d, 1 / 4d, 2 / 7d, 1 / 3d, 3 / 8d, 2 / 5d, 3 / 7d, 1 / 2d, 4 / 7d, 3 / 5d, 5 / 8d, 2 / 3d, 5 / 7d, 3 / 4d, 4 / 5d, 5 / 6d, 6 / 7d, 7 / 8d, 1d };
		List<Double> fareySequenceComputedList = new ArrayList<Double>(23);
		for (Double d : NumberGenerator.fareySequence(8).each()) {
			fareySequenceComputedList.add(d);
		}
		Double[] fareySequenceComputed = fareySequenceComputedList
				.toArray(new Double[0]);
		assertArrayEquals(fareySequence_8, fareySequenceComputed);
	}

	@Test(expected = NoSuchElementException.class)
	public void testWhileLoopWithSend() {
		NumberGenerator numberGenerator = new NumberGenerator(0);
		CoIterator<Integer, Boolean> numbers = numberGenerator.getNumbers();
		int startingNumber = numberGenerator.getStartingNumber();
		for (int i = 0; i < 1000; i++) {
			Integer number = numbers.send(Boolean.TRUE);
			assertEquals(startingNumber + i, number.intValue());
		}
		numbers.send(Boolean.FALSE);
	}
}
