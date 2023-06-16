package org.the4thlaw.bm3;

public interface ProgressReporter {
	void setStatus(String status);

	void setProgressUnknown(boolean unknown);

	void setTotal(int total);

	void setStep(int step);

	void reportError(String message);

	void setSubTotal(int total);

	void setSubStep(int step);

	void endSubTracking();
}
