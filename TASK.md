Coding Aufgabe für Java Backend Engineer Position

Aufgabe: Fetch, merge and serve data from two unreliable services
Build a small service that exposes GET /users/{id}/summary. It should fetch a user and
that user's posts from the two endpoints below concurrently, merge them, and return JSON.
• https://jsonplaceholder.typicode.com/users/{id}
• https://jsonplaceholder.typicode.com/posts?userId={id}

Assume these run in production behind someone else's SLA: the posts service sometimes
takes up to 3 s and fails about 10% of the time. Decide how your endpoint behaves in that
case and explain why.
AI tools are allowed. Use whatever you would use at work. Please also:
• commit as you go;
• add a short NOTES.md: your key decisions, what you deliberately left out, what you'd do
next, and where the AI helped or got it wrong;
• include tests for the paths you consider important.

Timebox: 2 hours. Provide the solution via a git hosting of your choice. In the follow-up we'll
walk through your code and extend it live together.