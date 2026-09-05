# RC-1 Architecture Boundary

Dependency direction:

`server/cli -> application -> core -> contracts`

Infrastructure adapters will implement contracts and may depend inward. Core/application must not import vendor packages.

Canonical ownership remains in the domain/application modules. Storage/search/vector/graph are replaceable adapters and projections, never canonical data owners.
