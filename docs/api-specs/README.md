# SwiftDrop OpenAPI contracts

SwiftDrop exposes one OpenAPI 3.1 document per downstream service. The API Gateway provides the centralized Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) and proxies each service document through its `/api/v1/<service>/v3/api-docs` route.

## Backend engineers

Run the backend services and open the centralized UI. Use the **Authorize** button to provide a JWT; the documentation endpoints themselves are intentionally readable without a token. Static documents exported by the SpringDoc Maven plugin are stored in this directory as JSON.

## Frontend engineers

From [frontend](../../frontend/), install dependencies and generate types from the checked-in baseline contracts:

```bash
npm install
npm run api:sync:specs
```

To generate types from the running gateway instead:

```bash
npm run api:sync:local
```

Generated TypeScript declarations are written to `frontend/src/types/generated/` and should be reviewed before being committed.

## QA engineers

Use the centralized UI for interactive endpoint checks and the YAML baselines for contract review. Keep a running gateway and the relevant downstream service available when using **Try it out**.
