#!/usr/bin/env python3
"""
Trash Types Configuration Loader

This module provides a singleton configuration loader for trash types.
It reads the trash-types.json file and provides methods for:
1. Getting the list of valid trash types
2. Mapping ML model outputs to trash types using the mlMappings configuration
"""

import json
import os
from typing import List, Dict, Optional


class TrashTypesConfig:
    """Singleton class for managing trash types configuration"""

    _instance = None
    _config = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(TrashTypesConfig, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        """Initialize the configuration (only runs once due to singleton)"""
        if self._config is None:
            self._load_config()

    def _load_config(self):
        """Load and validate the trash types configuration file"""
        # Get config path from environment or use default
        config_path = os.getenv("TRASH_TYPES_CONFIG_PATH", "../trash-types.json")

        # Validate file exists
        if not os.path.exists(config_path):
            raise FileNotFoundError(
                f"Trash types configuration file not found at: {config_path}\n"
                f"Please create this file or set TRASH_TYPES_CONFIG_PATH environment variable."
            )

        # Validate file is readable
        if not os.access(config_path, os.R_OK):
            raise PermissionError(
                f"Cannot read trash types configuration file at: {config_path}\n"
                f"Please check file permissions."
            )

        # Read and parse JSON
        try:
            with open(config_path, 'r') as f:
                self._config = json.load(f)
        except json.JSONDecodeError as e:
            raise ValueError(
                f"Failed to parse trash types configuration file at: {config_path}\n"
                f"Error: {str(e)}\n"
                f"Please ensure the file contains valid JSON."
            )
        except Exception as e:
            raise RuntimeError(
                f"Failed to load trash types configuration file at: {config_path}\n"
                f"Error: {str(e)}"
            )

        # Validate required fields
        if "trashTypes" not in self._config:
            raise ValueError(
                f"Trash types configuration is missing 'trashTypes' field at: {config_path}"
            )

        if "mlMappings" not in self._config:
            raise ValueError(
                f"Trash types configuration is missing 'mlMappings' field at: {config_path}"
            )

        # Validate trash types array is not empty
        if not self._config["trashTypes"]:
            raise ValueError(
                f"Trash types configuration has empty 'trashTypes' array at: {config_path}\n"
                f"The 'trashTypes' array must contain at least one trash type."
            )

        # Validate all trash type names are not blank
        for item in self._config["trashTypes"]:
            if "name" not in item or not item["name"].strip():
                raise ValueError(
                    f"Trash types configuration contains invalid trash type at: {config_path}\n"
                    f"All trash types must have a non-blank 'name' field."
                )

        # Validate ML mappings point to valid trash types
        trash_type_names = {item["name"] for item in self._config["trashTypes"]}
        invalid_mappings = {
            ml_output: target
            for ml_output, target in self._config["mlMappings"].items()
            if target not in trash_type_names
        }
        if invalid_mappings:
            invalid_list = ", ".join([f'"{k}" -> "{v}"' for k, v in invalid_mappings.items()])
            raise ValueError(
                f"Trash types configuration contains invalid ML mappings at: {config_path}\n"
                f"The following mappings point to non-existent trash types: {invalid_list}\n"
                f"All ML mappings must point to valid trash types."
            )

        print("✓ Trash types configuration loaded successfully")
        print(f"  Config path: {config_path}")
        print(f"  Trash types: {len(self._config['trashTypes'])}")
        print(f"  ML mappings: {len(self._config['mlMappings'])}")

    def get_trash_types(self) -> List[str]:
        """
        Get the list of valid trash type names

        Returns:
            List of trash type names
        """
        return [item["name"] for item in self._config["trashTypes"]]

    def map_ml_output(self, ml_output: str) -> Optional[str]:
        """
        Map an ML model output to a trash type

        Args:
            ml_output: The output from the ML model

        Returns:
            The mapped trash type name, or the original ml_output if it's already
            a valid trash type, or None if it doesn't map to anything valid

        Example:
            >>> config.map_ml_output("coca_cola_bottle")
            "plastic"
            >>> config.map_ml_output("plastic")  # already a valid type
            "plastic"
            >>> config.map_ml_output("unknown_item")
            None
        """
        # First check if it's already a valid trash type
        trash_types = self.get_trash_types()
        if ml_output in trash_types:
            return ml_output

        # Then check if it has a mapping
        if ml_output in self._config["mlMappings"]:
            return self._config["mlMappings"][ml_output]

        # No mapping found
        return None

    def map_trash_items(self, items: List[Dict]) -> List[Dict]:
        """
        Map a list of trash items from ML output to valid trash types

        Args:
            items: List of trash items with 'type' and 'amount' fields

        Returns:
            List of mapped trash items (may be shorter if some items were filtered out)

        Example:
            >>> items = [
            ...     {"type": "coca_cola_bottle", "amount": 3},
            ...     {"type": "unknown_item", "amount": 1}
            ... ]
            >>> config.map_trash_items(items)
            [{"type": "plastic", "amount": 3}]
        """
        mapped_items = []
        filtered_count = 0

        for item in items:
            ml_type = item.get("type", "")
            mapped_type = self.map_ml_output(ml_type)

            if mapped_type is not None:
                mapped_items.append({
                    "type": mapped_type,
                    "amount": item.get("amount", 0)
                })
                if mapped_type != ml_type:
                    print(f"  ℹ Mapped ML output '{ml_type}' -> '{mapped_type}'")
            else:
                filtered_count += 1
                print(f"  ⚠ Warning: ML output '{ml_type}' does not map to any valid trash type (filtered out)")
                # TODO: Add more ML model outputs to mlMappings in trash-types.json as you discover them

        if filtered_count > 0:
            print(f"  ⚠ Filtered out {filtered_count} invalid trash item(s)")

        return mapped_items


# Singleton accessor
_trash_config_instance = None


def get_trash_types_config() -> TrashTypesConfig:
    """
    Get the singleton TrashTypesConfig instance

    Returns:
        The TrashTypesConfig singleton instance

    Raises:
        FileNotFoundError: If the config file doesn't exist
        PermissionError: If the config file isn't readable
        ValueError: If the config file is invalid
        RuntimeError: If there's any other error loading the config
    """
    global _trash_config_instance
    if _trash_config_instance is None:
        _trash_config_instance = TrashTypesConfig()
    return _trash_config_instance
