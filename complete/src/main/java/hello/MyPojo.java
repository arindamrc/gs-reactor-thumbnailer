package hello;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class MyPojo {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	private int integer;
	private String string;
	private boolean condition;

	public int getInteger() {
		return integer;
	}

	public void setInteger(int integer) {
		this.integer = integer;
	}

	public String getString() {
		return string;
	}

	public void setString(String string) {
		this.string = string;
	}

	public boolean isCondition() {
		return condition;
	}

	public void setCondition(boolean condition) {
		this.condition = condition;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MyPojo [id=");
		builder.append(id);
		builder.append(", integer=");
		builder.append(integer);
		builder.append(", string=");
		builder.append(string);
		builder.append(", condition=");
		builder.append(condition);
		builder.append("]");
		return builder.toString();
	}


}
