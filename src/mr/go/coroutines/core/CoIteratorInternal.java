/*
 * Copyright [2009] [Marcin Rzeźnicki]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package mr.go.coroutines.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.CoroutineClosedException;
import mr.go.coroutines.user.CoroutineExitException;
import mr.go.coroutines.user.ExitCondition;
import mr.go.coroutines.user.ExitOnYieldedEqualsTo;

abstract class CoIteratorInternal<E, A> implements CoIterator<E, A> {

	@Override
	public void close() {
		final Frame frame = getFrame();
		if (frame.isCoroutineClosed()) {
			return;
		}
		frame.markCoroutineClosed();
		try {
			call(frame, null);
		} catch (CoroutineExitException e) {
		} catch (NoSuchElementException e) {
		} finally {
			frame.setState(Frame.CLOSED_STATE);
		}
	}

	@Override
	public Iterable<E> each() {
		return new CoIterable();
	}

	@Override
	public E next() {
		return call(getFrame(), null);
	}

	@Override
	public E send(A a) {
		return call(getFrame(), a);
	}

	@Override
	public java.lang.Iterable<E> till(E e) {
		return new CoIterableWithExitCondition(new ExitOnYieldedEqualsTo<E>(e));
	};

	@Override
	public Iterable<E> till(ExitCondition<E> condition) {
		return new CoIterableWithExitCondition(condition);
	}

	@Override
	public Iterable<E> till(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("count < 0");
		}
		return new CountingCoIterable(count);
	}

	protected abstract E call(Frame frame, A a);

	protected abstract Frame getFrame();

	private class CoIterable implements Iterable<E> {

		protected E			e;

		protected boolean	hasNext	= true;

		@Override
		public Iterator<E> iterator() {
			fetchNext();
			return new Iterator<E>() {

				@Override
				public boolean hasNext() {
					return hasNext;
				}

				@Override
				public E next() {
					if (!hasNext) {
						throw new NoSuchElementException();
					}
					E next = e;
					fetchNext();
					return next;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}

			};
		}

		protected void fetchNext() {
			try {
				e = next();
			} catch (NoSuchElementException e) {
				hasNext = false;
			} catch (CoroutineClosedException e) {
				hasNext = false;
			}
		}

	}

	private class CoIterableWithExitCondition extends CoIterable {

		private final ExitCondition<E>	cond;

		public CoIterableWithExitCondition(
				ExitCondition<E> cond) {
			this.cond = cond;
		}

		@Override
		protected void fetchNext() {
			super.fetchNext();
			if (hasNext) {
				hasNext = !cond.exit(e);
				if (!hasNext) {
					close();
				}
			}
		}
	}

	private class CountingCoIterable extends CoIterable {

		private int			calls;

		private final int	count;

		public CountingCoIterable(
				int count) {
			this.count = count;
		}

		@Override
		protected void fetchNext() {
			if (++calls > count) {
				close();
				hasNext = false;
			} else {
				super.fetchNext();
			}
		}
	}

}
