package net.jqwik.engine.matchers;

import org.mockito.*;

import net.jqwik.api.lifecycle.*;

class IsPropertyExecutionResultFailure implements ArgumentMatcher<PropertyExecutionResult> {
	private final String message;

	IsPropertyExecutionResultFailure(String message) {
		this.message = message;
	}

	@Override
	public boolean matches(PropertyExecutionResult result) {
		if (result.status() != PropertyExecutionResult.Status.FAILED)
			return false;
		if (message == null)
			return true;
		return result.throwable().get().getMessage().equals(message);
	}
}
