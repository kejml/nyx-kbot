resource "aws_dynamodb_table" "points" {
  name = "points"
  billing_mode = "PROVISIONED"
  read_capacity = 20
  write_capacity = 20
  hash_key = "discussionId"
  range_key = "questionId"

  attribute {
    name = "discussionId"
    type = "N"
  }

  attribute {
    name = "questionId"
    type = "N"
  }

  attribute {
    name = "postId"
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
    non_key_attributes = ["postId", "givenTo"]
  }

  local_secondary_index {
    name            = "lastId"
    projection_type = "KEYS_ONLY"
    range_key       = "postId"
  }
}
