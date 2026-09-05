from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class SessionAsset:
    asset_id: str
    responsibility: str
    canonical_owner: str
    version: str
    contract: str
    evidence_ref: str


class CanonicalSessionMergeRegistry:
    """Rejects duplicate canonical ownership across session handoffs."""

    def __init__(self) -> None:
        self._items: dict[str, SessionAsset] = {}
        self._by_responsibility: dict[str, SessionAsset] = {}

    def register(self, asset: SessionAsset) -> SessionAsset:
        existing = self._by_responsibility.get(asset.responsibility)
        if existing is not None and existing.canonical_owner != asset.canonical_owner:
            raise ValueError(
                f"canonical ownership conflict for {asset.responsibility}: "
                f"{existing.canonical_owner} != {asset.canonical_owner}"
            )
        if asset.asset_id in self._items:
            raise ValueError(f"duplicate asset id: {asset.asset_id}")
        if not asset.contract:
            raise ValueError("contract required")
        if not asset.evidence_ref:
            raise ValueError("evidence_ref required")
        self._items[asset.asset_id] = asset
        self._by_responsibility[asset.responsibility] = asset
        return asset

    def owner_for(self, responsibility: str) -> str | None:
        item = self._by_responsibility.get(responsibility)
        return None if item is None else item.canonical_owner
