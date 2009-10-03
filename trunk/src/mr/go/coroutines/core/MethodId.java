package mr.go.coroutines.core;

final class MethodId {

	public final String	descriptor;

	public final String	name;

	public MethodId(
			String name,
			String descriptor) {
		this.name = name;
		this.descriptor = descriptor;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (obj instanceof MethodId == false)
			return false;
		MethodId other = (MethodId) obj;
		if (!descriptor.equals(other.descriptor)) {
			return false;
		}
		if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + descriptor.hashCode();
		result = prime * result + name.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return name + descriptor;
	}

}
