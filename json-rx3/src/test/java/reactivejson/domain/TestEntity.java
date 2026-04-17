package reactivejson.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record TestEntity(int id, String name) {

	@JsonCreator
	public TestEntity(@JsonProperty("id") int id, @JsonProperty("name") String name) {
		this.id = id;
		this.name = name;
	}


	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TestEntity other)) return false;
		return other.id == id && Objects.equals(other.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id(), name());
	}
}
