resource "aws_dynamodb_table" "points" {
  name = "points"
  billing_mode = "PROVISIONED"
  read_capacity = 20
  write_capacity = 20
  hash_key = "discussionId"
  range_key = "id"

  attribute {
    name = "discussionId"
    type = "N"
  }

  attribute {
    name = "id"
    type = "N"
  }

  attribute {
    name = "givenDateTime"
    type = "S"
  }

  local_secondary_index {
    name            = "dateTimeIndex"
    projection_type = "INCLUDE"
    range_key       = "givenDateTime"
    non_key_attributes = ["id", "givenTo"]
  }
}
