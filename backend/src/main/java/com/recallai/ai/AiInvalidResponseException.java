package com.recallai.ai;

import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.util.List;

/**
 * The model answered, but the answer did not satisfy the schema or content rules. Carries the
 * specific problems so a corrective prompt can quote them back to the model.
 */
public class AiInvalidResponseException extends ApiException {

    private final List<String> problems;

    public AiInvalidResponseException(List<String> problems) {
        super(ErrorCode.AI_INVALID_RESPONSE, "The AI returned an invalid response: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
