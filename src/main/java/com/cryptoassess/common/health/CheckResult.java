package com.cryptoassess.common.health;

public record CheckResult(Status status, String detail) {

	public enum Status {

		UP, DOWN

	}

	public static CheckResult up(String detail) {
		return new CheckResult(Status.UP, detail);
	}

	public static CheckResult down(String detail) {
		return new CheckResult(Status.DOWN, detail);
	}

	public boolean isUp() {
		return this.status == Status.UP;
	}

}
