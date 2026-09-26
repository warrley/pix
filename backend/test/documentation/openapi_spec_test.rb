require "minitest/autorun"
require "yaml"

class OpenapiSpecTest < Minitest::Test
  SPEC_PATH = File.expand_path("../../docs/openapi.yaml", __dir__)

  def setup
    @spec = YAML.safe_load(File.read(SPEC_PATH), aliases: false)
  end

  def test_spec_declares_openapi_version_and_metadata
    assert_equal "3.1.0", @spec.fetch("openapi")
    assert_equal "RuBank API", @spec.dig("info", "title")
    refute_empty @spec.fetch("paths")
  end

  def test_spec_contains_the_public_api_path_groups
    expected_paths = %w[
      /up
      /api/v1/users
      /api/v1/accounts
      /api/v1/transfers
    ]

    expected_paths.each do |path|
      assert @spec.fetch("paths").key?(path), "missing OpenAPI path #{path}"
    end
  end

  def test_every_path_parameter_is_declared
    @spec.fetch("paths").each do |path, path_item|
      path_parameters = path.scan(/\{([^}]+)\}/).flatten
      declared_parameters = parameter_names(path_item["parameters"])

      path_item.each_value do |operation|
        next unless operation.is_a?(Hash)

        operation_parameters = parameter_names(operation["parameters"])

        (path_parameters - (declared_parameters + operation_parameters)).each do |name|
          flunk "path #{path} does not declare path parameter #{name}"
        end
      end
    end
  end

  private

  def parameter_names(parameters)
    Array(parameters).filter_map do |parameter|
      parameter = resolve_reference(parameter) if parameter.is_a?(Hash) && parameter["$ref"]
      parameter["name"] if parameter.is_a?(Hash) && parameter["in"] == "path"
    end
  end

  def resolve_reference(reference)
    node = @spec
    reference.fetch("$ref").sub(%r{^#/}, "").split("/").each do |key|
      node = node.fetch(key)
    end
    node
  end
end
