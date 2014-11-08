package ghadi;

import clojure.lang.RT;
import clojure.lang.IFn;
import clojure.lang.AFn;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.NoSuchElementException;

public final class XFIterable implements Iterable {
	private final IFn xform;
	private final Iterable iterable;

	public XFIterable(IFn xform, Iterable iterable) {
		this.xform = xform;
		this.iterable = iterable;
	}

	public Iterator iterator() {
		return new XFIterator(xform, iterable.iterator());
	}

	static final IFn append = new AFn() {
		public Object invoke(Object result) {
			return result;
		}

		public Object invoke(Object result, Object input) {
			XFIterator i = (XFIterator) result;
			i.buf.add(input);
			return i;
		}
	};

	private class XFIterator implements Iterator {

		private final IFn xform;
		private Iterator iter;

		private final ArrayList buf;
		private int pos = 0;

		public XFIterator(IFn xform, Iterator iter) {
			this.iter = iter;
			this.buf = new ArrayList();
			this.xform = (IFn) xform.invoke(append);
		}

		public boolean hasNext() {
			if (remaining())
				return true;
			pull();
			return remaining();
		}

		private boolean remaining() {
			if (pos < buf.size())
				return true;
			return false;
		}

		private Object bufNext() {
			Object ret = buf.get(pos);
			pos++;
			if (pos == buf.size()) {
				buf.clear();
				pos = 0;
			}
			return ret;
		}

		public Object next() {
			if (remaining()) {
				return bufNext();
			}
			pull();
			if (remaining()) {
				return bufNext();
			}
			throw new NoSuchElementException();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		private void pull() {
			if (iter == null)
				return;

			while (buf.size() == 0) {
				if (iter.hasNext()) {
					if (RT.isReduced(xform.invoke(this, iter.next()))) {
						iter = null;
						xform.invoke(this);
						return;
					}
				} else {
					iter = null;
					xform.invoke(this);
					return;
				}
			}
		}
	}

}
