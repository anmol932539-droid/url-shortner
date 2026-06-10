1. What did you ask the AI to do, and what did you write or decide yourself?

I used AI primarily as a design and implementation assistant to understand the URL shortener requirements, discuss API contracts, validate URLs, design short-code generation strategies, and understand redirect behavior.

I reviewed the generated code thoroughly, tested the implementation, and fixed issues where the behavior did not align with the requirements. For example, I corrected the handling of unknown short codes to ensure the service returns a 404 response.

The key design decisions were made by me, including:

The short-code generation strategy.
Returning 404 for unknown short codes.
Making custom alias creation idempotent when the same alias is requested for the same URL.
URL validation before creating mappings.


2. Where did you override, correct, or throw away the AI's output — and why?

I did not adopt AI-generated suggestions without review.

I evaluated multiple approaches for handling duplicate URLs and selected the one that best matched the requirements. I refined the alias handling logic to ensure aliases remain unique while still supporting idempotent requests for the same URL and alias combination.

I also simplified certain implementation details to keep the solution maintainable and easy to reason about.

Additionally, I removed an analytics API that was generated during development because it was outside the scope of the assignment requirements.

3. The two or three biggest trade-offs you made, and the alternatives you considered.
   Trade-off 1: Duplicate URL handling

Chosen approach:
Return the existing mapping when the same URL and alias are submitted again.

Alternative considered:
Generate a new short code for every request.

Reasoning:
The chosen approach is idempotent, avoids duplicate records, and provides a more predictable user experience.

Trade-off 2: Short-code generation

Chosen approach:
Use a deterministic short-code generation strategy that minimizes the possibility of collisions.

Alternative considered:
Generate random strings and retry in case of collisions.

Reasoning:
A deterministic approach provides stronger guarantees around uniqueness and is easier to reason about as the system scales.

4. What's missing, or what would you do with another day?

Given more time, I would improve the short-code generation strategy to make it more robust and less predictable.

My current implementation focuses on uniqueness, but I would move toward a scheme that combines an encoded incremental identifier with a random component. This would preserve uniqueness while making it significantly harder for users to infer or enumerate URLs based on sequential IDs alone.

I would also add more comprehensive testing around collision handling and edge cases in the code generation flow.