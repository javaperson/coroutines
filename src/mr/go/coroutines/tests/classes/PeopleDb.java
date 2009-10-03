package mr.go.coroutines.tests.classes;

import static mr.go.coroutines.user.Coroutines._;
import static mr.go.coroutines.user.Coroutines.yield;

import java.util.ArrayList;
import java.util.List;

import mr.go.coroutines.user.CoIterator;
import mr.go.coroutines.user.Coroutine;

public final class PeopleDb {

	private final List<String>	people;

	public PeopleDb(
			List<String> people) {
		this.people = new ArrayList<String>(people);
	}

	@Coroutine
	public CoIterator<String, String> people() {
		int current = 0;
		while (people.size() != 0) {
			String command = yield(people.get(current));
			if (command != null) {
				if (command.startsWith("add")) {
					String name = command.substring(4);
					people.add(name);
				} else if (command.startsWith("remove")) {
					String name = command.substring(7);
					people.remove(name);
				} else {
					throw new IllegalArgumentException(command);
				}
			}
			current = (people.size() == 0) ? -1 : (current + 1) % people.size();
		}
		return _();
	}
}
